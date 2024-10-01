package com.exchange.service;

import com.exchange.model.Order;
import com.exchange.model.Trade;
import com.exchange.repository.CustomTradeRepositoryImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisCallback;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

@Service
public class NewOrderbookService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final String BUY_SUFFIX = ":BUY";
    private final String SELL_SUFFIX = ":SELL";
    private final CustomTradeRepositoryImpl customTradeRepository;

    public NewOrderbookService(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper, CustomTradeRepositoryImpl customTradeRepository) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.customTradeRepository = customTradeRepository;
    }

    // 獲取對手方最佳訂單
    public Order getBestOpponentOrder(Order newOrder) {
        // 決定對手方的 key
        String opponentKey = newOrder.getSymbol() + (newOrder.getSide() == Order.Side.BUY ? SELL_SUFFIX : BUY_SUFFIX);

        // 從 Redis ZSet 中獲取對手方最佳價格的訂單
        Set<TypedTuple<Object>> opponentOrders = redisTemplate.opsForZSet().rangeWithScores(opponentKey, 0, 0);

        // 解析對手訂單
        if (opponentOrders != null && !opponentOrders.isEmpty()) {
            TypedTuple<Object> bestOpponent = opponentOrders.iterator().next();
            return parseOrderFromJson((String) bestOpponent.getValue());
        }

        return null;
    }

    // 解析 JSON 字符串到 Order 對象
    private Order parseOrderFromJson(String orderJson) {
        try {
            return objectMapper.readValue(orderJson, Order.class);
        } catch (JsonProcessingException e) {
            System.err.println("Error: Failed to parse JSON. Order JSON: " + orderJson);
            e.printStackTrace();
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }

    // 儲存訂單到 Redis
    public void saveOrderToRedis(Order order) {
        final byte[] redisKey = getRedisKeyBytes(order.getSymbol(), order.getSide());
        final double score = calculateScore(order);
        final byte[] jsonBytes = convertOrderToBytes(order);

        // 使用 ZSet 儲存完整的訂單 JSON
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            connection.zSetCommands().zAdd(redisKey, score, jsonBytes);
            return null;
        });
    }

    // 計算 ZSet 的 score
    private double calculateScore(Order order) {
        int precision = 7;
        BigDecimal precisionFactor = BigDecimal.TEN.pow(precision);
        Instant modifiedTime = order.getModifiedAt();
        BigDecimal calculatedScore = order.getPrice().multiply(precisionFactor)
                .add((order.getSide() == Order.Side.BUY ? BigDecimal.valueOf(-1) : BigDecimal.ONE).multiply(BigDecimal.valueOf(modifiedTime.toEpochMilli())));
        return calculatedScore.doubleValue();
    }

    // 從 Redis 中移除訂單
    public void removeOrderFromRedis(Order order) {
        byte[] redisKey = getRedisKeyBytes(order.getSymbol(), order.getSide());
        byte[] originalBytes = convertOrderToBytes(order);

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            connection.zSetCommands().zRem(redisKey, originalBytes);
            return null;
        });
    }

    // 更新 Redis 中的訂單
    public void updateOrderInRedis(Order order) {
        byte[] redisKey = getRedisKeyBytes(order.getSymbol(), order.getSide());
        double newScore = calculateScore(order);
        byte[] updatedBytes = convertOrderToBytes(order);

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            // 移除舊的訂單
            connection.zSetCommands().zRem(redisKey, updatedBytes);
            // 插入新的訂單
            connection.zSetCommands().zAdd(redisKey, newScore, updatedBytes);
            return null;
        });
    }

    // 保存交易到 MySQL
    public void saveTradeToDatabase(Trade trade) {
        // 更新買賣訂單的狀態（`buyOrder` 和 `sellOrder`）
        Order buyOrder = trade.getBuyOrder();
        Order sellOrder = trade.getSellOrder();

        // 使用自定義 repository 方法，同時保存訂單和交易
        customTradeRepository.saveAllOrdersAndTrade(buyOrder, sellOrder, trade);
    }

    // 獲取 Redis key 的字節數組
    private byte[] getRedisKeyBytes(String symbol, Order.Side side) {
        return (symbol + (side == Order.Side.BUY ? BUY_SUFFIX : SELL_SUFFIX)).getBytes(StandardCharsets.UTF_8);
    }

    // 轉換訂單為字節數組
    private byte[] convertOrderToBytes(Order order) {
        try {
            return objectMapper.writeValueAsBytes(order);
        } catch (JsonProcessingException e) {
            System.err.println("Error: Failed to convert order to JSON bytes. Order: " + order);
            e.printStackTrace();
            throw new RuntimeException("Failed to convert order to JSON bytes", e);
        }
    }
}
