package com.exchange.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

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

    // 批量獲取剩餘的訂單數量
    private Map<String, String> getRemainingOrderQuantities(List<String> orderIds) {
        // 將 List<String> 轉換為 List<Object>
        List<Object> objectOrderIds = new ArrayList<>(orderIds);

        // 批量查詢訂單的數量和已填充數量
        List<Object> quantities = redisTemplate.opsForHash().multiGet("orderQuantities", objectOrderIds);
        List<Object> filledQuantities = redisTemplate.opsForHash().multiGet("orderFilledQuantities", objectOrderIds);

        Map<String, String> remainingQuantities = new HashMap<>();
        for (int i = 0; i < orderIds.size(); i++) {
            if (quantities.get(i) != null && filledQuantities.get(i) != null) {
                double remaining = Double.parseDouble((String) quantities.get(i)) - Double.parseDouble((String) filledQuantities.get(i));
                remainingQuantities.put(orderIds.get(i), String.valueOf(remaining));
            }
        }
        return remainingQuantities;
    }
}
