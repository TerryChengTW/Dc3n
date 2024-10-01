package com.exchange.service;

import com.exchange.model.Order;
import com.exchange.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class OrderUpdateService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private OrderRepository orderRepository;


    // 更新訂單資料
    public void updateOrderInRedis(Order order) {

    }

    // 取消訂單，從 Redis 和 MySQL 刪除
    public void cancelOrder(Order order) {

    }
}
