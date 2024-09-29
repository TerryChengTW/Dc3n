package com.exchange.service;

import com.exchange.dto.TradeOrdersMessage;
import com.exchange.model.Order;
import com.exchange.model.Trade;
import com.exchange.utils.SnowflakeIdGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class NewOrderbookService {

    private final ObjectMapper objectMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // 以內存方式存儲每個交易對的訂單簿
    private final Map<String, SymbolOrderBook> symbolOrderBooks;

    public NewOrderbookService(ObjectMapper objectMapper, SnowflakeIdGenerator snowflakeIdGenerator, KafkaTemplate<String, String> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.kafkaTemplate = kafkaTemplate;
        this.symbolOrderBooks = new HashMap<>();
    }

    // 將訂單保存到內存中的訂單簿
    public void saveOrderToOrderBook(Order order) {
        SymbolOrderBook orderBook = symbolOrderBooks.computeIfAbsent(order.getSymbol(), k -> new SymbolOrderBook());
        orderBook.addOrder(order);
    }

    // 獲取最佳買單和賣單
    public Order[] getBestBuyAndSellOrders(String symbol) {
        SymbolOrderBook orderBook = symbolOrderBooks.get(symbol);
        if (orderBook == null) {
            return new Order[]{null, null};
        }

        Order bestBuyOrder = orderBook.getBestBuyOrder();
        Order bestSellOrder = orderBook.getBestSellOrder();

        return new Order[]{bestBuyOrder, bestSellOrder};
    }

    // 執行撮合
    public void executeTrade(Order buyOrder, Order sellOrder, BigDecimal matchedQuantity) {
        // 更新未成交數量
        buyOrder.setUnfilledQuantity(buyOrder.getUnfilledQuantity().subtract(matchedQuantity));
        sellOrder.setUnfilledQuantity(sellOrder.getUnfilledQuantity().subtract(matchedQuantity));

        // 更新已成交數量
        buyOrder.setFilledQuantity(buyOrder.getQuantity().subtract(buyOrder.getUnfilledQuantity()));
        sellOrder.setFilledQuantity(sellOrder.getQuantity().subtract(sellOrder.getUnfilledQuantity()));

        // 更新訂單狀態與時間
        updateOrderStatusAndTime(buyOrder);
        updateOrderStatusAndTime(sellOrder);

        // 從訂單簿中移除或更新訂單
        SymbolOrderBook orderBook = symbolOrderBooks.get(buyOrder.getSymbol());
        if (orderBook != null) {
            orderBook.updateOrRemoveOrder(buyOrder);
            orderBook.updateOrRemoveOrder(sellOrder);
        }

        // 發送交易結果到 Kafka
        sendTradeToKafka(buyOrder, sellOrder, matchedQuantity);
    }

    // 更新訂單狀態與時間
    private void updateOrderStatusAndTime(Order order) {
        if (order.getUnfilledQuantity().compareTo(BigDecimal.ZERO) == 0) {
            order.setStatus(Order.OrderStatus.COMPLETED);
        } else {
            order.setStatus(Order.OrderStatus.PARTIALLY_FILLED);
        }
        order.setUpdatedAt(Instant.now());
    }

    // 發送交易結果到 Kafka
    private void sendTradeToKafka(Order buyOrder, Order sellOrder, BigDecimal matchedQuantity) {
        // 異步處理
        CompletableFuture.runAsync(() -> {
            try {
                // 創建 Trade 對象
                Trade tradeObject = new Trade();
                tradeObject.setId(String.valueOf(snowflakeIdGenerator.nextId()));
                tradeObject.setBuyOrder(buyOrder);
                tradeObject.setSellOrder(sellOrder);
                tradeObject.setSymbol(buyOrder.getSymbol());
                tradeObject.setPrice(buyOrder.getPrice());
                tradeObject.setQuantity(matchedQuantity);
                tradeObject.setTradeTime(Instant.now());

                // 創建新的 TradeOrdersMessage，包含買賣訂單和交易
                TradeOrdersMessage tradeOrdersMessage = new TradeOrdersMessage();
                tradeOrdersMessage.setBuyOrder(buyOrder);
                tradeOrdersMessage.setSellOrder(sellOrder);
                tradeOrdersMessage.setTrade(tradeObject);

                // 發送到 Kafka topic
                kafkaTemplate.send("matched_orders", objectMapper.writeValueAsString(tradeOrdersMessage));

            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        });
    }
}
