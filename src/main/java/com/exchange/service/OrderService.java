package com.exchange.service;

import com.exchange.model.Order;
import com.exchange.producer.OrderProducer;
import com.exchange.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class OrderService {

    @Autowired
    private OrderProducer orderProducer;

    @Autowired
    private OrderMatchingService orderMatchingService;  // 注入 OrderMatchingService

    @Autowired
    private OrderRepository orderRepository;  // 仍然可以用於查詢 MySQL

    // 保存新訂單，並將其發送到 Kafka
    public void saveOrder(Order order) {
        orderProducer.sendOrder(order);
    }

    // 更新訂單，並將其發送到 Kafka
    public void updateOrder(Order order) {
        orderProducer.sendOrder(order);
    }

    // 根據訂單 ID 查詢訂單，優先查詢 Redis，若 Redis 中沒有，則查詢 MySQL
    public Optional<Order> getOrderById(String id) {
        // 先從 Redis 中查詢訂單
        Order orderFromRedis = orderMatchingService.getOrderFromRedis(id);
        if (orderFromRedis != null) {
            return Optional.of(orderFromRedis);
        }

        // 如果 Redis 中沒有，則從 MySQL 查詢
        return orderRepository.findById(id);
    }
}
