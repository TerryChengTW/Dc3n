package com.exchange.consumer;

import com.exchange.dto.MatchedMessage;
import com.exchange.model.Order;
import com.exchange.model.Trade;
import com.exchange.repository.OrderRepository;
import com.exchange.repository.TradeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MatchedOrderConsumer {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(topics = "matched_orders", groupId = "order_group")
    public void consumeMatchedMessage(String messageJson) {
        try {
            // 反序列化消息
            MatchedMessage message = objectMapper.readValue(messageJson, MatchedMessage.class);

            // 根據消息類型處理數據
            if ("ORDER".equals(message.getType())) {
                Order order = objectMapper.readValue(message.getData(), Order.class);
                orderRepository.save(order);
            } else if ("TRADE".equals(message.getType())) {
                Trade trade = objectMapper.readValue(message.getData(), Trade.class);
                tradeRepository.save(trade);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
