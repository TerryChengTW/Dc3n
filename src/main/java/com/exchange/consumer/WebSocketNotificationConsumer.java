package com.exchange.consumer;

import com.exchange.producer.WebSocketNotification;
import com.exchange.websocket.WebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class WebSocketNotificationConsumer {

    @Autowired
    private WebSocketHandler webSocketHandler;

    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(topics = "websocket_notifications", groupId = "websocket_group")
    public void consumeNotification(String notificationJson) {
        try {
            WebSocketNotification notification = objectMapper.readValue(notificationJson, WebSocketNotification.class);
            webSocketHandler.sendMessageToUser(notification.getUserId(), notificationJson);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}