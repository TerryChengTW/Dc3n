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
    private OrderRepository orderRepository;

    @Autowired
    private OrderProducer orderProducer;

    public Order saveOrder(Order order) {
        Order savedOrder = orderRepository.save(order);
        // 發送訂單到 Kafka
        orderProducer.sendOrder(savedOrder);
        return savedOrder;
    }

    public Order updateOrder(Order order) {
        Order updatedOrder = orderRepository.save(order);
        // 發送更新的訂單到 Kafka
        orderProducer.sendOrder(updatedOrder);
        return updatedOrder;
    }

    public Optional<Order> getOrderById(String id) {
        return orderRepository.findById(id);
    }
}
