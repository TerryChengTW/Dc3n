package com.exchange.service;

import com.exchange.model.Order;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisCallback;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class NewOrderbookService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // Redis key suffix constants for buy and sell sides
    private final String BUY_SUFFIX = ":BUY";
    private final String SELL_SUFFIX = ":SELL";

    public NewOrderbookService(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
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

    // 儲存訂單到 Redis 並同時獲取最佳買賣單
    public Order[] getBestBuyAndSellOrders(String symbol) {
        // Redis keys
        String buyKey = symbol + BUY_SUFFIX;
        String sellKey = symbol + SELL_SUFFIX;

        // 同時查詢最高買價和最低賣價的訂單
        List<Object> bestOrders = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            connection.zRevRangeWithScores(buyKey.getBytes(StandardCharsets.UTF_8), 0, 0); // 查詢最高買價訂單
            connection.zRangeWithScores(sellKey.getBytes(StandardCharsets.UTF_8), 0, 0); // 查詢最低賣價訂單
            return null;
        });

        // 解析查詢結果
        Set<TypedTuple<Object>> bestBuyOrderSet = (Set<TypedTuple<Object>>) bestOrders.get(0);
        Set<TypedTuple<Object>> bestSellOrderSet = (Set<TypedTuple<Object>>) bestOrders.get(1);

        // 獲取最佳買單和賣單
        Order bestBuyOrder = parseBestOrder(bestBuyOrderSet);
        Order bestSellOrder = parseBestOrder(bestSellOrderSet);

        return new Order[]{bestBuyOrder, bestSellOrder};
    }

    // 解析最佳訂單
    private Order parseBestOrder(Set<TypedTuple<Object>> bestOrderSet) {
        if (bestOrderSet == null || bestOrderSet.isEmpty()) {
            return null; // 無訂單
        }
        TypedTuple<Object> bestOrderTuple = bestOrderSet.iterator().next();
        String orderJson = (String) bestOrderTuple.getValue();
        return parseOrderFromJson(orderJson);
    }

    private Order parseOrderFromJson(String orderJson) {
        try {
            return objectMapper.readValue(orderJson, Order.class);
        } catch (JsonProcessingException e) {
            System.err.println("Error: Failed to parse JSON. Order JSON: " + orderJson);
            e.printStackTrace();
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }

    // 執行撮合
    public void executeTrade(Order buyOrder, Order sellOrder, BigDecimal matchedQuantity) {

        // 保存原始的 ZSet value
        byte[] originalBuyBytes = convertOrderToBytes(buyOrder);
        byte[] originalSellBytes = convertOrderToBytes(sellOrder);

        // 更新未成交數量
        buyOrder.setUnfilledQuantity(buyOrder.getUnfilledQuantity().subtract(matchedQuantity));
        sellOrder.setUnfilledQuantity(sellOrder.getUnfilledQuantity().subtract(matchedQuantity));

        // 更新已成交數量
        buyOrder.setFilledQuantity(buyOrder.getQuantity().subtract(buyOrder.getUnfilledQuantity()));
        sellOrder.setFilledQuantity(sellOrder.getQuantity().subtract(sellOrder.getUnfilledQuantity()));


        // 在一個 Redis pipeline 中執行所有操作
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            // 處理買單
            handleOrderUpdateOrRemove(connection, buyOrder, originalBuyBytes);

            // 處理賣單
            handleOrderUpdateOrRemove(connection, sellOrder, originalSellBytes);

            return null;
        });

        // 保存交易結果到數據庫
        saveTradeToDatabase(buyOrder, sellOrder, matchedQuantity);
    }

    // 更新或移除訂單
    private void handleOrderUpdateOrRemove(RedisConnection connection, Order order, byte[] originalBytes) {
        byte[] redisKey = getRedisKeyBytes(order.getSymbol(), order.getSide());

        // 如果未成交數量為零，移除訂單
        if (order.getUnfilledQuantity().compareTo(BigDecimal.ZERO) == 0) {
            connection.zSetCommands().zRem(redisKey, originalBytes);
        } else {
            // 更新訂單：先移除原始訂單，再保存更新後的訂單
            double score = calculateScore(order);
            byte[] updatedBytes = convertOrderToBytes(order);

            connection.zSetCommands().zRem(redisKey, originalBytes);
            connection.zSetCommands().zAdd(redisKey, score, updatedBytes);
        }
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

    private void saveTradeToDatabase(Order buyOrder, Order sellOrder, BigDecimal matchedQuantity) {
        // 執行將交易記錄保存到 MySQL 的邏輯
    }
}
