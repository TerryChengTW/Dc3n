package com.exchange.service;

import com.exchange.model.Order;
import com.exchange.producer.OrderBookDeltaProducer;
import com.exchange.producer.OrderProducer;
import com.exchange.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@Service
public class OrderService {

    private final OrderProducer orderProducer;

    private final OrderRepository orderRepository;

    private final RedisTemplate<String, String> redisTemplate;

    private final ObjectMapper objectMapper;

    private final OrderBookDeltaProducer orderBookDeltaProducer;

    public OrderService(OrderProducer orderProducer, OrderRepository orderRepository, RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper, OrderBookDeltaProducer orderBookDeltaProducer) {
        this.orderProducer = orderProducer;
        this.orderRepository = orderRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.orderBookDeltaProducer = orderBookDeltaProducer;
    }


    // 保存新訂單，並將其發送到 Kafka
    public void saveOrder(Order order) {
        validateOrder(order);  // 檢查訂單類型和價格

        // 發送新訂單到 Kafka
        orderProducer.sendNewOrder(order);
    }

    public Optional<Order> getOrderById(String orderId) {
        // 先在 Redis 中查找
        Order order = findOrderInRedis(orderId);
        if (order != null) {
            return Optional.of(order);
        }

        // 如果 Redis 中沒有，則在 MySQL 中查找
        return orderRepository.findById(orderId);
    }

    private Order findOrderInRedis(String orderId) {
        String[] sides = {"BUY", "SELL"};
        String[] symbols = {"BTCUSDT"}; // 您可能需要擴展此列表以包含所有支持的交易對

        for (String symbol : symbols) {
            for (String side : sides) {
                String key = symbol + ":" + side;
                Set<String> orders = redisTemplate.opsForZSet().range(key, 0, -1);
                if (orders != null) {
                    for (String orderJson : orders) {
                        try {
                            Order order = objectMapper.readValue(orderJson, Order.class);
                            if (order.getId().equals(orderId)) {
                                return order;
                            }
                        } catch (Exception e) {
                            // 處理 JSON 解析錯誤
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return null;
    }

    // 更新訂單
    public Order updateOrder(Order order, BigDecimal newQuantity, BigDecimal newPrice) {
        validateOrder(order);  // 確保訂單類型和價格有效

        // 取得已成交數量
        BigDecimal filledQuantity = order.getFilledQuantity();

        if (order.getStatus() == Order.OrderStatus.PENDING || order.getStatus() == Order.OrderStatus.PARTIALLY_FILLED) {
            // 檢查新數量是否大於成交數量
            if (newQuantity.compareTo(filledQuantity) <= 0) {
                throw new IllegalArgumentException("更新的數量不能小於已成交的數量");
            }

            // 從 Redis 中刪除舊訂單
            removeOrderFromRedis(order);

            // 更新訂單資料
            order.setQuantity(newQuantity);
            order.setPrice(newPrice);
            order.setUnfilledQuantity(newQuantity.subtract(filledQuantity));
            order.setUpdatedAt(Instant.now());
            order.setModifiedAt(Instant.now());

            // 將更新後的訂單添加到 Redis
            addOrderToRedis(order);

            // 保存到數據庫（如果需要）
            orderRepository.save(order);

            return order;
        } else {
            throw new IllegalArgumentException("只有 PENDING 或 PARTIALLY_FILLED 狀態的訂單可以更新");
        }
    }

    private void removeOrderFromRedis(Order order) {
        String key = order.getSymbol() + ":" + order.getSide();
        Set<String> orders = redisTemplate.opsForZSet().range(key, 0, -1);
        if (orders != null) {
            String orderJsonToRemove = null;
            for (String orderJson : orders) {
                try {
                    Order tempOrder = objectMapper.readValue(orderJson, Order.class);
                    if (tempOrder.getId().equals(order.getId())) {
                        orderJsonToRemove = orderJson;
                        break;
                    }
                } catch (Exception e) {
                    // 處理 JSON 解析錯誤
                    e.printStackTrace();
                }
            }
            if (orderJsonToRemove != null) {
                redisTemplate.opsForZSet().remove(key, orderJsonToRemove);
                orderBookDeltaProducer.sendDelta(
                        order.getSymbol(),
                        order.getSide().toString(),
                        order.getPrice().toString(),
                        "-" + order.getUnfilledQuantity().toString()
                );
            } else {
                throw new RuntimeException("Redis ZSet 中未找到該訂單");
            }
        } else {
            throw new RuntimeException("Redis ZSet 中未找到該訂單");
        }
    }


    private void addOrderToRedis(Order order) {
        String key = order.getSymbol() + ":" + order.getSide();
        try {
            String orderJson = objectMapper.writeValueAsString(order);
            // 計算 score，根據您的需求，可以使用價格或其他策略
            double score = calculateScore(order);
            redisTemplate.opsForZSet().add(key, orderJson, score);
            orderBookDeltaProducer.sendDelta(
                    order.getSymbol(),
                    order.getSide().toString(),
                    order.getPrice().toString(),
                    order.getUnfilledQuantity().toString()
            );
        } catch (Exception e) {
            throw new RuntimeException("訂單序列化失敗", e);
        }
    }

    private double calculateScore(Order order) {
        int precision = 7;
        BigDecimal precisionFactor = BigDecimal.TEN.pow(precision);
        Instant modifiedTime = order.getModifiedAt();
        BigDecimal calculatedScore = order.getPrice().multiply(precisionFactor)
                .add((order.getSide() == Order.Side.BUY ? BigDecimal.valueOf(-1) : BigDecimal.ONE).multiply(BigDecimal.valueOf(modifiedTime.toEpochMilli())));
        return calculatedScore.doubleValue();
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

    // 取消訂單
    public void cancelOrder(Order order) {
        if (order.getStatus() == Order.OrderStatus.PENDING || order.getStatus() == Order.OrderStatus.PARTIALLY_FILLED) {
            // 從 Redis 中刪除訂單
            removeOrderFromRedis(order);

            // 更新訂單狀態
            order.setStatus(Order.OrderStatus.CANCELLED);
            order.setUpdatedAt(Instant.now());
            order.setModifiedAt(Instant.now());

            // 保存到數據庫
            orderRepository.save(order);
        } else {
            throw new IllegalArgumentException("只有 PENDING 或 PARTIALLY_FILLED 狀態的訂單可以取消");
        }
    }

}
