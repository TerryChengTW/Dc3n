package com.exchange.service;

import com.exchange.model.Order;
import com.exchange.producer.OrderProducer;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderProducer orderProducer;

    public OrderService(OrderProducer orderProducer) {
        this.orderProducer = orderProducer;
    }

    // 保存新訂單，並將其發送到 Kafka
    public void saveOrder(Order order) {
        validateOrder(order);  // 檢查訂單類型和價格

        // 發送新訂單到 Kafka
        orderProducer.sendNewOrder(order);
    }

    // 驗證訂單的有效性
    private void validateOrder(Order order) {
        if (order.getOrderType() == Order.OrderType.MARKET && order.getPrice() != null) {
            throw new IllegalArgumentException("市價單不應該設置價格");
        }
        if (order.getOrderType() == Order.OrderType.LIMIT && order.getPrice() == null) {
            throw new IllegalArgumentException("限價單需要設置價格");
        }
    }
}
