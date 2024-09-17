package com.exchange.producer;

import com.exchange.model.Order;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderProducer {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;  // 用來轉換成JSON

    // 發送新訂單到 Kafka
    public void sendNewOrder(Order order) {
        sendOrderToTopic(order, "new_orders");
    }

    // 發送更新訂單到 Kafka
    public void sendUpdateOrder(Order order) {
        sendOrderToTopic(order, "update_orders");
    }

    // 發送取消訂單到 Kafka
    public void sendCancelOrder(Order order) {
        sendOrderToTopic(order, "cancel_orders");
    }

    // 通用方法，發送訂單到不同的 Kafka topic
    private void sendOrderToTopic(Order order, String topic) {
        try {
            String orderJson = objectMapper.writeValueAsString(order);
            kafkaTemplate.send(topic, orderJson);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }
}
