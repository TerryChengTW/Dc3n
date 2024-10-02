package com.exchange.producer;

import com.exchange.dto.MatchedMessage;
import com.exchange.dto.TradeOrdersMessage;
import com.exchange.model.Order;
import com.exchange.model.Trade;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MatchedOrderProducer {

    private static final String TOPIC = "matched_orders";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper; // 用來轉換成 JSON

    // 發送匹配交易
    public void sendMatchedTrade(TradeOrdersMessage tradeOrdersMessage) {
        sendToKafka("TRADE_ORDER", tradeOrdersMessage);
    }

    // 發送消息到 Kafka
    private void sendToKafka(String type, Object data) {
        try {
            // 封裝 MatchedMessage
            MatchedMessage message = new MatchedMessage(type, objectMapper.writeValueAsString(data));
            // 序列化 MatchedMessage 成 JSON
            String messageJson = objectMapper.writeValueAsString(message);
            // 發送到 Kafka
            kafkaTemplate.send(TOPIC, messageJson);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }
}
