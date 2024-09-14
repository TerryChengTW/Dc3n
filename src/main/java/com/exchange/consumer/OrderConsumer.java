package com.exchange.consumer;

import com.exchange.model.Order;
import com.exchange.service.OrderMatchingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class OrderConsumer {

    private final OrderMatchingService matchingService;
    private final ObjectMapper objectMapper;

    public OrderConsumer(OrderMatchingService matchingService, ObjectMapper objectMapper) {
        this.matchingService = matchingService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "orders", groupId = "order_group")
    public void consumeOrder(String orderJson) {
        try {
            // 將接收到的 JSON 訂單轉換為 Order 對象
            Order order = objectMapper.readValue(orderJson, Order.class);

            // 將訂單保存到 Redis 並嘗試匹配
            matchingService.processOrder(order);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
