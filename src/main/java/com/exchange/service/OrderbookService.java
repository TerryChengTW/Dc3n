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
            for (ZSetOperations.TypedTuple<String> entry : entries) {
                String orderId = entry.getValue();
                Double price = entry.getScore();
                String remainingQuantity = getRemainingOrderQuantity(orderId);
                if (remainingQuantity != null) {
                    result.add(Arrays.asList(price.toString(), remainingQuantity));
                }
            }
        }

        return result;
    }

    // 獲取剩餘的訂單數量（總數量 - 已匹配數量）
    private String getRemainingOrderQuantity(String orderId) {
        String orderKey = "order:" + orderId;
        String quantity = (String) redisTemplate.opsForHash().get(orderKey, "quantity");
        String filledQuantity = (String) redisTemplate.opsForHash().get(orderKey, "filledQuantity");

        // 確保兩者都不為空並且是有效數字
        if (quantity != null && filledQuantity != null) {
            double remainingQuantity = Double.parseDouble(quantity) - Double.parseDouble(filledQuantity);
            return String.valueOf(remainingQuantity);
        }
        return null;
    }
}
