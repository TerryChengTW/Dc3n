package com.exchange.service;

import com.exchange.model.Order;
import com.exchange.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderUpdateService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderMatchingService orderMatchingService;

    // 更新訂單資料
    public void updateOrderInRedis(Order order) {
        String orderKey = "order:" + order.getId();
        redisTemplate.opsForHash().put(orderKey, "price", order.getPrice().toString());
        redisTemplate.opsForHash().put(orderKey, "quantity", order.getQuantity().toString());
        redisTemplate.opsForHash().put(orderKey, "updatedAt", order.getUpdatedAt().toString());
        redisTemplate.opsForHash().put(orderKey, "modifiedAt", order.getModifiedAt().toString());

        String orderBookKey = "orderbook:" + order.getSymbol() + ":" + order.getSide();
        redisTemplate.opsForZSet().remove(orderBookKey, order.getId());
        redisTemplate.opsForZSet().add(orderBookKey, order.getId(), order.getPrice().doubleValue());

        orderMatchingService.sendWebSocketNotification(order.getUserId(), "ORDER_UPDATED", order);
        orderMatchingService.sendOrderbookUpdate(order.getSymbol());
    }

    // 取消訂單，從 Redis 和 MySQL 刪除
    public void cancelOrder(Order order) {
        String orderKey = "order:" + order.getId();
        redisTemplate.delete(orderKey);  // 從 Redis 中刪除

        String orderBookKey = "orderbook:" + order.getSymbol() + ":" + order.getSide();  // 假設 Side 是 "BUY" 或 "SELL"
        redisTemplate.opsForZSet().remove(orderBookKey, order.getId());

        order.setModifiedAt(java.time.LocalDateTime.now());  // 更新時間戳
        order.setStatus(Order.OrderStatus.CANCELLED);  // 設置訂單狀態
        orderRepository.save(order);  // 更新 MySQL 中的狀態
        orderMatchingService.sendWebSocketNotification(order.getUserId(), "ORDER_COMPLETED", order);
        orderMatchingService.sendOrderbookUpdate(order.getSymbol());
    }
}