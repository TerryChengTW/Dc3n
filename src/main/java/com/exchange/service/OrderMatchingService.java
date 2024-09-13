package com.exchange.service;

import com.exchange.model.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@EnableAsync
public class OrderMatchingService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Order> ordersToPersist = new ArrayList<>();
    private final int BATCH_SIZE = 1000;

    @Async
    public void saveOrdersToDatabase(List<Order> orders) {
        long startMySQL = System.currentTimeMillis();
        String sql = "INSERT INTO orders (order_id, user_id, symbol, price, quantity, order_type, status, stop_price, take_profit_price, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        for (Order order : orders) {
            jdbcTemplate.update(sql,
                    order.getOrderId(),
                    order.getUser().getId(),
                    order.getSymbol(),
                    order.getPrice().doubleValue(),
                    order.getQuantity().doubleValue(),
                    order.getOrderType().name(),
                    order.getStatus().name(),
                    order.getStopPrice() != null ? order.getStopPrice().doubleValue() : null,
                    order.getTakeProfitPrice() != null ? order.getTakeProfitPrice().doubleValue() : null,
                    order.getCreatedAt(),
                    order.getUpdatedAt());
        }
        long endMySQL = System.currentTimeMillis();
        System.out.println("MySQL batch operation took " + (endMySQL - startMySQL) + " ms");
    }

    public void processOrders(List<Order> orders) {
        long start = System.currentTimeMillis();

        // Redis 處理
        String key = "orderbook:" + orders.get(0).getSymbol();
        for (Order order : orders) {
            redisTemplate.opsForZSet().add(key, order, order.getPrice().doubleValue());
        }
        long endRedis = System.currentTimeMillis();
        System.out.println("Redis batch operation took " + (endRedis - start) + " ms");

        // MySQL 批量持久化
        saveOrdersToDatabase(orders);
    }

    public void processOrder(Order order) {
        ordersToPersist.add(order);
        if (ordersToPersist.size() >= BATCH_SIZE) {
            processOrders(new ArrayList<>(ordersToPersist));
            ordersToPersist.clear();
        }
    }

    public void flushPendingOrders() {
        if (!ordersToPersist.isEmpty()) {
            processOrders(new ArrayList<>(ordersToPersist));
            ordersToPersist.clear();
        }
    }
}