package com.exchange.consumer;

import com.exchange.dto.OrderDTO;
import com.exchange.websocket.UserOrderWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class UserOrderConsumer {
    private final UserOrderWebSocketHandler userOrderWebSocketHandler;
    private final ObjectMapper objectMapper;

    @Autowired
    public UserOrderConsumer(UserOrderWebSocketHandler userOrderWebSocketHandler, ObjectMapper objectMapper) {
        this.userOrderWebSocketHandler = userOrderWebSocketHandler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "user-order-updates",
            groupId = "#{T(java.util.UUID).randomUUID().toString()}",  // 動態生成唯一的 groupId
            containerFactory = "kafkaListenerContainerFactory",
            properties = {"auto.offset.reset=latest"}
    )
    public void consume(String message) throws IOException {
        OrderDTO orderDTO = objectMapper.readValue(message, OrderDTO.class);
        userOrderWebSocketHandler.sendOrderNotification(orderDTO.getUserId(), "ORDER_UPDATED", orderDTO);
    }
}
