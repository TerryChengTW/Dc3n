package com.exchange.producer;

import com.exchange.model.Order;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.kafka.support.SendResult;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class OrderProducer {

    private static final Logger logger = LoggerFactory.getLogger(OrderProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public OrderProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    // 發送新訂單到 Kafka
    public void sendNewOrder(Order order) {
        sendOrderToTopic(order, "new_orders");
    }

    // 通用方法，發送訂單到不同的 Kafka topic
    private void sendOrderToTopic(Order order, String topic) {
        String orderJson = convertOrderToJson(order);
        if (orderJson != null) {
            // 使用 CompletableFuture 處理 Kafka 發送結果
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, orderJson);

            // 當消息成功發送時
            future.thenAccept(result -> {
                // 這裡可以選擇記錄成功的消息發送結果，根據需要
                logger.debug("Message sent successfully to topic: {}", topic);
            });

            // 當消息發送失敗時
            future.exceptionally(ex -> {
                // 記錄發送失敗的錯誤
                logger.error("Failed to send message to Kafka topic {}: {}", topic, ex.getMessage());
                return null;
            });
        }
    }

    // 將 Order 物件轉換成 JSON，處理 JsonProcessingException
    private String convertOrderToJson(Order order) {
        try {
            return objectMapper.writeValueAsString(order);
        } catch (JsonProcessingException e) {
            logger.error("Failed to convert order to JSON", e);  // 僅記錄錯誤
            return null;
        }
    }
}
