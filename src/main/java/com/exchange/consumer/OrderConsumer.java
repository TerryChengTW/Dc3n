package com.exchange.consumer;

import com.exchange.model.Order;
import com.exchange.service.OrderMatchingService;
import com.exchange.service.OrderUpdateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class OrderConsumer {

    private final OrderMatchingService matchingService;
    private final OrderUpdateService updateService;
    private final ObjectMapper objectMapper;

    public OrderConsumer(OrderMatchingService matchingService, OrderUpdateService updateService, ObjectMapper objectMapper) {
        this.matchingService = matchingService;
        this.updateService = updateService;
        this.objectMapper = objectMapper;
    }

    // 消費新訂單
    @KafkaListener(topics = "new_orders", groupId = "order_group")
    public void consumeNewOrder(String orderJson) {
        try {
            // 將接收到的 JSON 訂單轉換為 Order 對象
            Order order = objectMapper.readValue(orderJson, Order.class);

            // 將訂單保存到 Redis 並嘗試匹配
            matchingService.processOrder(order);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 消費更新訂單
    @KafkaListener(topics = "update_orders", groupId = "order_group")
    public void consumeUpdateOrder(String orderJson) {
        try {
            // 將接收到的 JSON 訂單轉換為 Order 對象
            Order order = objectMapper.readValue(orderJson, Order.class);

            // 更新訂單到 Redis 和 MySQL
            updateService.updateOrderInRedis(order);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 消費取消訂單
    @KafkaListener(topics = "cancel_orders", groupId = "order_group")
    public void consumeCancelOrder(String orderJson) {
        try {
            // 將接收到的 JSON 訂單轉換為 Order 對象
            Order order = objectMapper.readValue(orderJson, Order.class);

            // 取消訂單並更新 Redis 和 MySQL
            updateService.cancelOrder(order);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
