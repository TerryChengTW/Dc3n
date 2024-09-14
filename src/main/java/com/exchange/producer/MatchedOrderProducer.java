package com.exchange.producer;

import com.exchange.dto.MatchedMessage;
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
    private ObjectMapper objectMapper; // 用來轉換成JSON

    public void sendMatchedOrder(Order order) {
        sendToKafka("ORDER", order);
    }

    public void sendMatchedTrade(Trade trade) {
        sendToKafka("TRADE", trade);
    }

    private void sendToKafka(String type, Object data) {
        try {
            MatchedMessage message = new MatchedMessage(type, objectMapper.writeValueAsString(data));
            String messageJson = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(TOPIC, messageJson);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }
}
