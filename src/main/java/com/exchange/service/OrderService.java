package com.exchange.service;

import com.exchange.model.Order;
import com.exchange.producer.OrderProducer;
import com.exchange.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Service
public class OrderService {

    @Autowired
    private OrderProducer orderProducer;

    @Autowired
    private OrderRepository orderRepository;
//
//    @Autowired
//    private OrderMatchingService orderMatchingService;  // 用於保存新訂單到 Redis 並進行匹配

    // 保存新訂單，並將其發送到 Kafka
    public void saveOrder(Order order) {
        validateOrder(order);  // 檢查訂單類型和價格

        // 發送新訂單到 Kafka
        orderProducer.sendNewOrder(order);
    }

    // 更新訂單，並將其發送到 Kafka
    public void updateOrder(Order order, BigDecimal newQuantity) {
        validateOrder(order);  // 確保訂單類型和價格有效

        // 取得已成交數量和總數量
        BigDecimal filledQuantity = order.getFilledQuantity();

        if (order.getStatus() == Order.OrderStatus.PENDING) {
            // PENDING 狀態可以直接更新
        } else if (order.getStatus() == Order.OrderStatus.PARTIALLY_FILLED) {
            // 檢查新數量是否大於成交數量
            if (newQuantity.compareTo(filledQuantity) < 0) {
                throw new IllegalArgumentException("更新的數量不能小於成交的數量");
            }
        } else {
            throw new IllegalArgumentException("只有PENDING狀態的訂單可以更新");
        }

        // 如果檢查通過，發送更新訂單到 Kafka
        orderProducer.sendUpdateOrder(order);
    }

    // 取消訂單，並將其發送到 Kafka
    public void cancelOrder(Order order) {
        order.setStatus(Order.OrderStatus.CANCELLED);  // 設置訂單狀態為取消
        order.setUpdatedAt(Instant.now());  // 更新時間戳

        // 發送取消訂單到 Kafka
        orderProducer.sendCancelOrder(order);
    }

//    // 根據訂單 ID 查詢訂單
//    public Optional<Order> getOrderById(String id) {
//        // 優先從 Redis 查詢訂單
//        Order orderFromRedis = orderMatchingService.getOrderFromRedis(id);
//        if (orderFromRedis != null) {
//            return Optional.of(orderFromRedis);
//        }
//
//        // 如果 Redis 中沒有找到，從 MySQL 查詢
//        Optional<Order> orderFromDB = orderRepository.findById(id);
//        if (orderFromDB.isPresent()) {
//            // 記錄日誌，確認是從 MySQL 查到的訂單
//            System.out.println("訂單從 MySQL 中查詢到: " + orderFromDB.get().getId());
//        } else {
//            // 記錄日誌，確認沒有找到訂單
//            System.out.println("訂單未找到: " + id);
//        }
//        return orderFromDB;
//    }

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
