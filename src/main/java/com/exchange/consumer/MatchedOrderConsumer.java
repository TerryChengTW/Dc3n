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

import java.util.*;

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

    // 定時任務，每隔1秒檢查一次是否有未持久化的數據
    @Scheduled(fixedDelay = 1000)
    public void checkAndPersistBatch() {
        List<TradeOrdersMessage> batchToProcess;
        // 鎖定 orderMessageBatch 以保證一致性
        synchronized (orderMessageBatch) {
            // 只有當有未處理訂單時才檢查
            if (hasPendingOrders) {
                // 取得批次，並清空 orderMessageBatch 中的 "batch"
                batchToProcess = new ArrayList<>(orderMessageBatch.getOrDefault("batch", new ArrayList<>()));
                orderMessageBatch.remove("batch");
                hasPendingOrders = false; // 標誌處理完成
            } else {
                // 如果沒有未處理的訂單則返回
                return;
            }
        }
        // 處理批次訂單
        processOrderBatch(batchToProcess);
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

                synchronized (orderMessageBatch) {
                    // 累積消息到批次列表
                    orderMessageBatch.computeIfAbsent("batch", k -> new ArrayList<>()).add(tradeOrdersMessage);
                    hasPendingOrders = true; // 標誌有新的消息需要處理

                    // 當累積到一定批次時，批量處理
                    if (orderMessageBatch.get("batch").size() >= BATCH_SIZE) {
                        List<TradeOrdersMessage> batchToProcess = new ArrayList<>(orderMessageBatch.get("batch"));
                        orderMessageBatch.remove("batch");
                        hasPendingOrders = false; // 標誌處理完成
                        // 在同步塊之外處理批次
                        processOrderBatch(batchToProcess);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 錯誤處理
        }
    }

    private void processOrderBatch(List<TradeOrdersMessage> messages) {
        Map<String, Order> buyOrderMap = new HashMap<>(); // 用於累積 buyOrders
        Map<String, Order> sellOrderMap = new HashMap<>(); // 用於累積 sellOrders
        List<Trade> trades = new ArrayList<>();

        for (TradeOrdersMessage message : messages) {
            // 累積 buyOrder
            buyOrderMap.merge(message.getBuyOrder().getId(), message.getBuyOrder(), this::mergeOrders);

            // 累積 sellOrder
            sellOrderMap.merge(message.getSellOrder().getId(), message.getSellOrder(), this::mergeOrders);

            // 添加 trade
            trades.add(message.getTrade());
        }

        // 從 Map 中獲取最終需要保存的訂單列表
        List<Order> buyOrders = new ArrayList<>(buyOrderMap.values());
        List<Order> sellOrders = new ArrayList<>(sellOrderMap.values());

        // 將訂單和交易保存到數據庫
        customTradeRepository.saveAllOrdersAndTrades(buyOrders, sellOrders, trades);
    }


    // 自定義合併 Order 的邏輯
    private Order mergeOrders(Order existingOrder, Order newOrder) {
        // 更新 filledQuantity 等需要累積的屬性
        existingOrder.setFilledQuantity(existingOrder.getFilledQuantity().add(newOrder.getFilledQuantity()));
        existingOrder.setUnfilledQuantity(newOrder.getUnfilledQuantity()); // 或者其他累積邏輯
        existingOrder.setUpdatedAt(newOrder.getUpdatedAt()); // 更新時間戳等其他必要屬性
        return existingOrder;
    }
}
