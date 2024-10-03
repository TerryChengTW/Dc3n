package com.exchange.service;

import com.exchange.model.Order;
import com.exchange.model.Trade;
import com.exchange.producer.OrderBookDeltaProducer;
import com.exchange.producer.UserOrderProducer;
import com.exchange.utils.SnowflakeIdGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class NewOrderMatchingService {

    private final NewOrderbookService orderbookService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final OrderBookDeltaProducer orderBookDeltaProducer;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final UserOrderProducer userOrderProducer;

    @Autowired
    public NewOrderMatchingService(NewOrderbookService orderbookService,
                                   SnowflakeIdGenerator snowflakeIdGenerator,
                                   OrderBookDeltaProducer orderBookDeltaProducer,
                                   KafkaTemplate<String, String> kafkaTemplate,
                                   ObjectMapper objectMapper,
                                   UserOrderProducer userOrderProducer) {
        this.orderbookService = orderbookService;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.orderBookDeltaProducer = orderBookDeltaProducer;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.userOrderProducer = userOrderProducer;
    }

    public void handleNewOrder(Order order) throws JsonProcessingException {
        // 在嘗試將新訂單存入 Redis 之前，先進行撮合
        matchOrders(order);

        // 未完全匹配的訂單才存入 Redis
        if (order.getUnfilledQuantity().compareTo(BigDecimal.ZERO) > 0) {
//            System.out.println("保存新訂單到 Redis: " + order);
            orderbookService.saveOrderToRedis(order);
            // 推送增量數據
            orderBookDeltaProducer.sendDelta(
                    order.getSymbol(),
                    order.getSide().toString(),
                    order.getPrice().toString(),
                    order.getUnfilledQuantity().toString()
            );
        }

    }

    // 撮合邏輯
    public void matchOrders(Order newOrder) throws JsonProcessingException {
        // 保存所有匹配到的 `Trade`
        List<Trade> matchedTrades = new ArrayList<>();

        while (newOrder.getUnfilledQuantity().compareTo(BigDecimal.ZERO) > 0) {
            Order p1 = orderbookService.getBestOpponentOrder(newOrder);

            if (p1 == null) {
                break;
            }

            // 保存原始對手訂單的 JSON 值
            String originalP1Json = orderbookService.convertOrderToJson(p1);

            boolean isPriceMatch = (newOrder.getSide() == Order.Side.BUY && newOrder.getPrice().compareTo(p1.getPrice()) >= 0) ||
                    (newOrder.getSide() == Order.Side.SELL && newOrder.getPrice().compareTo(p1.getPrice()) <= 0);

            if (isPriceMatch) {
                BigDecimal matchedQuantity = newOrder.getUnfilledQuantity().min(p1.getUnfilledQuantity());

                // 更新訂單數量和狀態
                newOrder.setFilledQuantity(newOrder.getFilledQuantity().add(matchedQuantity));
                newOrder.setUnfilledQuantity(newOrder.getUnfilledQuantity().subtract(matchedQuantity));

                p1.setFilledQuantity(p1.getFilledQuantity().add(matchedQuantity));
                p1.setUnfilledQuantity(p1.getUnfilledQuantity().subtract(matchedQuantity));

                // 更新狀態
                updateOrdersStatus(List.of(newOrder, p1));

                // 建立 `Trade`
                Trade trade = new Trade();
                trade.setId(String.valueOf(snowflakeIdGenerator.nextId()));
                trade.setBuyOrder(newOrder.getSide() == Order.Side.BUY ? newOrder : p1);
                trade.setSellOrder(newOrder.getSide() == Order.Side.SELL ? newOrder : p1);
                trade.setSymbol(newOrder.getSymbol());
                trade.setPrice(p1.getPrice());  // 確保交易價格為對手方訂單的價格
                trade.setQuantity(matchedQuantity);
                trade.setTradeTime(Instant.now());
                trade.setDirection(newOrder.getSide() == Order.Side.BUY ? "buy" : "sell");

                // 將 `Trade` 加入列表中
                matchedTrades.add(trade);
                String tradeJson = objectMapper.writeValueAsString(trade);
//                System.out.println("保存新交易到 Kafka: " + tradeJson);
                kafkaTemplate.send("recent-trades", tradeJson);
                // 更新 `p1` 在 Redis 中的狀態
                if (p1.getUnfilledQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    orderbookService.removeOrderFromRedis(p1, originalP1Json);
                } else {
                    orderbookService.updateOrderInRedis(p1, originalP1Json);
                }

                // 推送對手訂單增量數據
                orderBookDeltaProducer.sendDelta(
                        p1.getSymbol(),
                        p1.getSide().toString(),
                        p1.getPrice().toString(),
                        "-" + matchedQuantity // 本次成交的數量，以負值表示減少
                );

                userOrderProducer.sendOrderUpdate(p1);

            } else {
                break;
            }
        }

        // 保存所有的交易和訂單到 MySQL
        if (!matchedTrades.isEmpty()) {
            // 使用自定義 repository 同時保存所有 `Order` 和 `Trade`
            orderbookService.saveAllOrdersAndTrades(matchedTrades);
        }
    }

    // 更新訂單狀態和時間
    private void updateOrdersStatus(List<Order> orders) {
        for (Order order : orders) {
            // 如果未成交數量為零，訂單狀態更新為 `COMPLETED`
            if (order.getUnfilledQuantity().compareTo(BigDecimal.ZERO) == 0) {
                order.setStatus(Order.OrderStatus.COMPLETED);
            } else {
                order.setStatus(Order.OrderStatus.PARTIALLY_FILLED);
            }

            // 更新 `updatedAt` 字段為當前時間
            order.setUpdatedAt(Instant.now());
        }
    }


}
