package com.exchange.consumer;

import com.exchange.model.Order;
import com.exchange.service.NewOrderMatchingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class OrderConsumer {

    private final ObjectMapper objectMapper;
    private final NewOrderMatchingService newMatchingService;

    public OrderConsumer(
                         ObjectMapper objectMapper,
                         @Lazy NewOrderMatchingService newMatchingService) {
        this.objectMapper = objectMapper;
        this.newMatchingService = newMatchingService;
    }

    // 消費新訂單
    @KafkaListener(topics = "new_orders", groupId = "order_group")
    public void consumeNewOrder(String orderJson) {
        try {
            // 將接收到的 JSON 訂單轉換為 Order 對象
            Order order = objectMapper.readValue(orderJson, Order.class);
            newMatchingService.handleNewOrder(order);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
