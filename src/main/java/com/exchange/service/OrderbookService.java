package com.exchange.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisHashCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class OrderbookService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    // 獲取訂單簿快照，返回bids、asks以及時間戳
    public Map<String, Object> getOrderbookSnapshot(String symbol) {
        String buyOrderbookKey = "orderbook:" + symbol + ":BUY";
        String sellOrderbookKey = "orderbook:" + symbol + ":SELL";

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("symbol", symbol);
        snapshot.put("bids", getOrderbookEntries(buyOrderbookKey, true));
        snapshot.put("asks", getOrderbookEntries(sellOrderbookKey, false));
        snapshot.put("timestamp", System.currentTimeMillis());
        System.out.println(snapshot);
        return snapshot;
    }

    // 根據訂單類型獲取訂單數據（買單或賣單）
    private List<List<String>> getOrderbookEntries(String orderbookKey, boolean isBuyOrder) {
        Set<ZSetOperations.TypedTuple<String>> entries;
        if (isBuyOrder) {
            // 買單按價格降序排列
            entries = redisTemplate.opsForZSet().reverseRangeWithScores(orderbookKey, 0, 499);
        } else {
            // 賣單按價格升序排列
            entries = redisTemplate.opsForZSet().rangeWithScores(orderbookKey, 0, 499);
        }

        List<List<String>> result = new ArrayList<>();
        if (entries != null) {
            List<String> orderIds = new ArrayList<>();
            for (ZSetOperations.TypedTuple<String> entry : entries) {
                orderIds.add(entry.getValue());
            }
            System.out.println("OrderIds: " + orderIds);

            // 批量獲取剩餘訂單數量
            Map<String, String> remainingQuantities = getRemainingOrderQuantities(orderIds);

            for (ZSetOperations.TypedTuple<String> entry : entries) {
                String orderId = entry.getValue();
                Double price = entry.getScore();
                String remainingQuantity = remainingQuantities.get(orderId);
                if (remainingQuantity != null) {
                    result.add(Arrays.asList(price.toString(), remainingQuantity));
                }
            }
        }

        return result;
    }

    // 使用 Redis Pipeline 批量查詢剩餘的訂單數量
    private Map<String, String> getRemainingOrderQuantities(List<String> orderIds) {
        Map<String, String> remainingQuantities = new HashMap<>();

        // 使用 RedisTemplate 批量查詢
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            RedisHashCommands hashCommands = connection.hashCommands(); // 使用 RedisHashCommands
            for (String orderId : orderIds) {
                String orderKey = "order:" + orderId;
                hashCommands.hGet(orderKey.getBytes(StandardCharsets.UTF_8), "quantity".getBytes(StandardCharsets.UTF_8));
                hashCommands.hGet(orderKey.getBytes(StandardCharsets.UTF_8), "filledQuantity".getBytes(StandardCharsets.UTF_8));
            }
            return null; // RedisCallback 要返回 null，讓 executePipelined 知道已完成批量操作
        });

        // results 列表將包含所有的 quantity 和 filledQuantity
        for (int i = 0; i < orderIds.size(); i++) {
            String quantity = (String) results.get(i * 2); // 每兩個結果一組，偶數位置是 quantity
            String filledQuantity = (String) results.get(i * 2 + 1); // 奇數位置是 filledQuantity

            if (quantity != null && filledQuantity != null) {
                double remaining = Double.parseDouble(quantity) - Double.parseDouble(filledQuantity);
                remainingQuantities.put(orderIds.get(i), String.valueOf(remaining));
            }
        }

        return remainingQuantities;
    }
}
