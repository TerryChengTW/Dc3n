package com.exchange.consumer;

import com.exchange.dto.MatchedMessage;
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

    @Transactional
    @KafkaListener(topics = "matched_orders", groupId = "order_group")
    public void consumeTradeOrdersMessage(String messageJson) {
        try {
            // 反序列化 MatchedMessage
            MatchedMessage matchedMessage = objectMapper.readValue(messageJson, MatchedMessage.class);

            // 檢查消息類型
            if ("TRADE_ORDER".equals(matchedMessage.getType())) {
                // 反序列化 TradeOrdersMessage
                TradeOrdersMessage tradeOrdersMessage = objectMapper.readValue(matchedMessage.getData(), TradeOrdersMessage.class);

                // 提取買單、賣單和交易
                Order buyOrder = tradeOrdersMessage.getBuyOrder();
                Order sellOrder = tradeOrdersMessage.getSellOrder();
                Trade trade = tradeOrdersMessage.getTrade();

                try {
                    // 使用自定義方法一次性保存所有對象
                    tradeRepository.saveAllOrdersAndTrade(buyOrder, sellOrder, trade);
                } catch (ObjectOptimisticLockingFailureException e) {
                    // 處理樂觀鎖異常
                    System.err.println("Optimistic lock failure: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 錯誤處理
        }
    }
}
