package com.exchange.consumer;

import com.exchange.dto.TradeOrdersMessage;
import com.exchange.model.Order;
import com.exchange.model.Trade;
import com.exchange.repository.TradeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchedOrderConsumer {

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional // 一個事務內處理
    @KafkaListener(topics = "matched_orders", groupId = "order_group")
    public void consumeTradeOrdersMessage(String messageJson) {
        try {
            // 反序列化新的 TradeOrdersMessage
            TradeOrdersMessage message = objectMapper.readValue(messageJson, TradeOrdersMessage.class);

            // 提取買單、賣單和交易
            Order buyOrder = message.getBuyOrder();
            Order sellOrder = message.getSellOrder();
            Trade trade = message.getTrade();

            try {
                // 使用自定義方法一次性保存所有對象
                tradeRepository.saveAllOrdersAndTrade(buyOrder, sellOrder, trade);
            } catch (ObjectOptimisticLockingFailureException e) {
                // 處理樂觀鎖異常：可能進行重試或放棄操作
                System.err.println("Optimistic lock failure: " + e.getMessage());
                // 可根據需求進行重試或其他操作
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 錯誤處理
        }
    }
}
