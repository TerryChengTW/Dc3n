package com.exchange.service;

import com.exchange.model.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderbookSnapshotService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public OrderbookSnapshotService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getOrderbookSnapshot(String symbol, BigDecimal interval) {
        long startTime = System.currentTimeMillis(); // 開始計時

        // 默認的價格區間為 1000，如果傳入為 null 則使用默認值
        BigDecimal priceInterval = (interval != null) ? interval : BigDecimal.valueOf(1000);

        // Lua 腳本，原子性地獲取買賣雙方的訂單
        String luaScript =
                "local buyOrders = redis.call('ZRANGE', KEYS[1], 0, -1, 'WITHSCORES') " +
                        "local sellOrders = redis.call('ZRANGE', KEYS[2], 0, -1, 'WITHSCORES') " +
                        "return {buyOrders, sellOrders}";

        // 構建 Redis 腳本對象
        RedisScript<List> script = new DefaultRedisScript<>(luaScript, List.class);
        List<Object> result = redisTemplate.execute(script, Arrays.asList(symbol + ":BUY", symbol + ":SELL"));

        // 解析結果
        List<Object> buyOrders = (List<Object>) result.get(0);
        List<Object> sellOrders = (List<Object>) result.get(1);

        // 聚合買單和賣單快照
        Map<BigDecimal, BigDecimal> buySnapshot = aggregateOrderSnapshot(parseOrders(buyOrders), priceInterval, true);
        Map<BigDecimal, BigDecimal> sellSnapshot = aggregateOrderSnapshot(parseOrders(sellOrders), priceInterval, false);

        // 組裝最終快照
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("buy", getTopNOrders(buySnapshot, 5, true));
        snapshot.put("sell", getTopNOrders(sellSnapshot, 5, false));

        long endTime = System.currentTimeMillis(); // 結束計時
        long duration = endTime - startTime; // 計算執行時間

        System.out.println("執行時間: " + duration + " 毫秒");

        return snapshot;
    }

    private Map<BigDecimal, BigDecimal> aggregateOrderSnapshot(Set<ZSetOperations.TypedTuple<String>> orders, BigDecimal priceInterval, boolean isBuy) {
        Map<BigDecimal, BigDecimal> snapshot = new HashMap<>();

        if (orders != null) {
            for (ZSetOperations.TypedTuple<String> orderTuple : orders) {
                try {
                    // 解析訂單 JSON
                    Order order = objectMapper.readValue(orderTuple.getValue(), Order.class);
                    BigDecimal orderPrice = order.getPrice();
                    BigDecimal orderQuantity = order.getUnfilledQuantity();

                    // 計算價格區間的 key
                    BigDecimal intervalPrice = calculateIntervalPrice(orderPrice, priceInterval, isBuy);

                    // 聚合對應價格區間的訂單數量
                    snapshot.merge(intervalPrice, orderQuantity, BigDecimal::add);
                } catch (Exception e) {
                    e.printStackTrace(); // 處理解析異常
                }
            }
        }

        return snapshot;
    }

    private Set<ZSetOperations.TypedTuple<String>> parseOrders(List<Object> orders) {
        Set<ZSetOperations.TypedTuple<String>> parsedOrders = new LinkedHashSet<>();
        // 將 Lua 腳本返回的數據轉換為所需的訂單格式
        for (int i = 0; i < orders.size(); i += 2) {
            String orderJson = (String) orders.get(i);
            Double score = Double.parseDouble((String) orders.get(i + 1));
            parsedOrders.add(new DefaultTypedTuple<>(orderJson, score));
        }
        return parsedOrders;
    }

    private Map<BigDecimal, BigDecimal> getTopNOrders(Map<BigDecimal, BigDecimal> snapshot, int n, boolean isBuy) {
        // 根據價格排序並取前 n 個
        return snapshot.entrySet().stream()
                .sorted(isBuy ? Map.Entry.<BigDecimal, BigDecimal>comparingByKey().reversed() :
                        Map.Entry.comparingByKey()) // 對於買單按降序排序，賣單按升序排序
                .limit(n) // 取前 n 筆
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (oldValue, newValue) -> oldValue,
                        LinkedHashMap::new // 保持插入順序
                ));
    }

    private BigDecimal calculateIntervalPrice(BigDecimal orderPrice, BigDecimal priceInterval, boolean isBuy) {
        // 根據價格和區間大小計算對應的價格區間
        BigDecimal intervalPrice = orderPrice.divide(priceInterval).setScale(0, isBuy ? BigDecimal.ROUND_FLOOR : BigDecimal.ROUND_CEILING);
        return intervalPrice.multiply(priceInterval);
    }
}
