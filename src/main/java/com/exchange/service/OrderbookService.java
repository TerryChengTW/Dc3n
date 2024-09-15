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

    private List<List<String>> getOrderbookEntries(String orderbookKey, boolean isBuyOrder) {
        Set<ZSetOperations.TypedTuple<String>> entries;
        if (isBuyOrder) {
            // 買單按價格降序排列
            entries = redisTemplate.opsForZSet().reverseRangeWithScores(orderbookKey, 0, 9);
        } else {
            // 賣單按價格升序排列
            entries = redisTemplate.opsForZSet().rangeWithScores(orderbookKey, 0, 9);
        }

        List<List<String>> result = new ArrayList<>();

        if (entries != null) {
            for (ZSetOperations.TypedTuple<String> entry : entries) {
                String orderId = entry.getValue();
                Double price = entry.getScore();
                String quantity = getOrderQuantity(orderId);
                if (quantity != null) {
                    result.add(Arrays.asList(price.toString(), quantity));
                }
            }
        }

        return result;
    }

    private String getOrderQuantity(String orderId) {
        String orderKey = "order:" + orderId;
        return (String) redisTemplate.opsForHash().get(orderKey, "quantity");
    }
}