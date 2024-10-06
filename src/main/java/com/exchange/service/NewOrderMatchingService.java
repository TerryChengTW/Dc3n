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
        // 檢查訂單類型，根據類型選擇匹配邏輯
        if (order.getOrderType() == Order.OrderType.MARKET) {
            // 如果是市價單，執行市價單匹配
            System.out.println("市價單匹配中...");
            matchMarketOrder(order);
            System.out.println("市價單匹配完成");
        } else {
            // 如果是限價單，執行限價單匹配
            matchOrders(order);
        }

        // 未完全匹配的限價單才存入 Redis
        if (order.getUnfilledQuantity().compareTo(BigDecimal.ZERO) > 0 && order.getOrderType() != Order.OrderType.MARKET) {
            orderbookService.saveOrderToRedis(order);
            // 推送增量數據
            orderBookDeltaProducer.sendDelta(
                    order.getSymbol(),
                    order.getSide().toString(),
                    order.getPrice().toString(),
                    order.getUnfilledQuantity().toString()
            );
        }

        // 推送訂單更新到 Kafka
        userOrderProducer.sendOrderUpdate(order);
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
                trade.setTakerOrderId(newOrder.getId());  // 設置 taker 訂單 ID

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

    // 新增方法來處理市價單
    public void matchMarketOrder(Order marketOrder) throws JsonProcessingException {
        // 保存所有匹配到的 `Trade`
        List<Trade> matchedTrades = new ArrayList<>();

        // 市價單不需要關注價格，只需要立即匹配對手方訂單
        while (marketOrder.getUnfilledQuantity().compareTo(BigDecimal.ZERO) > 0) {
            // 獲取最優對手方訂單
            Order p1 = orderbookService.getBestOpponentOrder(marketOrder);

            // 如果沒有可以匹配的訂單，則結束
            if (p1 == null) {
                break;
            }

            // 保存原始對手訂單的 JSON 值
            String originalP1Json = orderbookService.convertOrderToJson(p1);

            // 市價單完全按可成交數量匹配
            BigDecimal matchedQuantity = marketOrder.getUnfilledQuantity().min(p1.getUnfilledQuantity());

            // 更新訂單數量和狀態
            marketOrder.setFilledQuantity(marketOrder.getFilledQuantity().add(matchedQuantity));
            marketOrder.setUnfilledQuantity(marketOrder.getUnfilledQuantity().subtract(matchedQuantity));

            p1.setFilledQuantity(p1.getFilledQuantity().add(matchedQuantity));
            p1.setUnfilledQuantity(p1.getUnfilledQuantity().subtract(matchedQuantity));

            // 更新狀態
            updateOrdersStatus(List.of(marketOrder, p1));

            // 建立 `Trade`
            Trade trade = new Trade();
            trade.setId(String.valueOf(snowflakeIdGenerator.nextId()));
            trade.setBuyOrder(marketOrder.getSide() == Order.Side.BUY ? marketOrder : p1);
            trade.setSellOrder(marketOrder.getSide() == Order.Side.SELL ? marketOrder : p1);
            trade.setSymbol(marketOrder.getSymbol());
            trade.setPrice(p1.getPrice());  // 市價單的價格取對手方訂單的價格
            trade.setQuantity(matchedQuantity);
            trade.setTradeTime(Instant.now());
            trade.setDirection(marketOrder.getSide() == Order.Side.BUY ? "buy" : "sell");
            trade.setTakerOrderId(marketOrder.getId());  // 設置 taker 訂單 ID

            // 將 `Trade` 加入列表中
            matchedTrades.add(trade);
            String tradeJson = objectMapper.writeValueAsString(trade);
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

            // 推送訂單更新到 Kafka
            userOrderProducer.sendOrderUpdate(p1);
        }

        // 保存所有的交易和訂單到 MySQL
        if (!matchedTrades.isEmpty()) {
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
