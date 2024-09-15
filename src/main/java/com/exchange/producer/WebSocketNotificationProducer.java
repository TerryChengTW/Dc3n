package com.exchange.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class WebSocketNotificationProducer {

    private static final String TOPIC = "websocket_notifications";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    public void sendNotification(String userId, String eventType, Object data) {
        try {
            WebSocketNotification notification = new WebSocketNotification(userId, eventType, data);
            String notificationJson = objectMapper.writeValueAsString(notification);
            kafkaTemplate.send(TOPIC, userId, notificationJson);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}