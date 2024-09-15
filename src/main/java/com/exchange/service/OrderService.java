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
    private OrderMatchingService orderMatchingService;

    @Autowired
    private OrderRepository orderRepository;

    // 保存新訂單，並將其發送到 Kafka
    public void saveOrder(Order order) {
        // 檢查訂單類型和價格
        validateOrder(order);

        // 發送訂單到 Kafka
        orderProducer.sendOrder(order);
    }

    // 更新訂單，並將其發送到 Kafka
    public void updateOrder(Order order) {
        // 檢查訂單類型和價格
        validateOrder(order);

        // 發送訂單到 Kafka
        orderProducer.sendOrder(order);
    }

    // 根據訂單 ID 查詢訂單
    public Optional<Order> getOrderById(String id) {
        Order orderFromRedis = orderMatchingService.getOrderFromRedis(id);
        if (orderFromRedis != null) {
            return Optional.of(orderFromRedis);
        }
        return orderRepository.findById(id);
    }

    // 驗證訂單的價格是否符合類型要求
    private void validateOrder(Order order) {
        if (order.getOrderType() == Order.OrderType.MARKET && order.getPrice() != null) {
            throw new IllegalArgumentException("市價單不應該有價格");
        }

        if (order.getOrderType() == Order.OrderType.LIMIT && order.getPrice() == null) {
            throw new IllegalArgumentException("限價單需要指定價格");
        }
    }
}
