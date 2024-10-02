package com.exchange.consumer;

import com.exchange.dto.MatchedMessage;
import com.exchange.dto.TradeOrdersMessage;
import com.exchange.model.Order;
import com.exchange.model.Trade;
import com.exchange.repository.CustomTradeRepositoryImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@EnableScheduling
public class MatchedOrderConsumer {

    private final ObjectMapper objectMapper;

    private final CustomTradeRepositoryImpl customTradeRepository;

    private final Map<String, List<TradeOrdersMessage>> orderMessageBatch = new HashMap<>();
    private static final int BATCH_SIZE = 10;
    private volatile boolean hasPendingOrders = false;

    public MatchedOrderConsumer(ObjectMapper objectMapper, CustomTradeRepositoryImpl customTradeRepository) {
        this.objectMapper = objectMapper;
        this.customTradeRepository = customTradeRepository;
    }

    @Transactional
    @KafkaListener(topics = "matched_orders", groupId = "order_group")
    public void consumeTradeOrdersMessage(String messageJson) {
        try {
            // 反序列化 MatchedMessage
            MatchedMessage matchedMessage = objectMapper.readValue(messageJson, MatchedMessage.class);
            if ("TRADE_ORDER".equals(matchedMessage.getType())) {
                // 反序列化 TradeOrdersMessage
                TradeOrdersMessage tradeOrdersMessage = objectMapper.readValue(matchedMessage.getData(), TradeOrdersMessage.class);

                // 累積消息到批次列表
                orderMessageBatch.computeIfAbsent("batch", k -> new ArrayList<>()).add(tradeOrdersMessage);
                hasPendingOrders = true; // 標誌有新的消息需要處理

                // 當累積到一定批次時，批量處理
                if (orderMessageBatch.get("batch").size() >= BATCH_SIZE) {
                    processOrderBatch(orderMessageBatch.get("batch"));
                    orderMessageBatch.remove("batch");
                    hasPendingOrders = false; // 標誌處理完成
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 錯誤處理
        }
    }

    // 定時任務，每隔1秒檢查一次是否有未持久化的數據
    @Scheduled(fixedDelay = 1000)
    public void checkAndPersistBatch() {
        // 只有在有未處理訂單時才檢查
        if (hasPendingOrders) {
            List<TradeOrdersMessage> batch = orderMessageBatch.get("batch");
            if (batch != null && !batch.isEmpty()) {
                processOrderBatch(batch);
                orderMessageBatch.remove("batch");
                hasPendingOrders = false; // 標誌處理完成
            }
        }
    }

    private void processOrderBatch(List<TradeOrdersMessage> messages) {
        // 根據累積的消息按順序寫入數據庫
        List<Order> buyOrders = new ArrayList<>();
        List<Order> sellOrders = new ArrayList<>();
        List<Trade> trades = new ArrayList<>();

        for (TradeOrdersMessage message : messages) {
            buyOrders.add(message.getBuyOrder());
            sellOrders.add(message.getSellOrder());
            trades.add(message.getTrade());
        }

        // 批量寫入或更新到數據庫
        customTradeRepository.saveAllOrdersAndTrades(buyOrders, sellOrders, trades);
    }
}
