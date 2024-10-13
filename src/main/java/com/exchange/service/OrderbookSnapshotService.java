package com.exchange.service;

import com.exchange.model.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
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

    public Map<String, List<OrderbookSnapshot>> getOrderbookSnapshot(String symbol, BigDecimal interval) {
        // 開始計時
        long startTime = System.currentTimeMillis();

        // 查詢賣單，從低到高
        List<Order> sellOrders = getOrdersFromRedis(symbol + ":SELL", false);

        // 查詢買單，從高到低（倒序）
        List<Order> buyOrders = getOrdersFromRedis(symbol + ":BUY", true);

        // 計算賣單五檔，並按價格從低到高排列
        List<OrderbookSnapshot> sellSnapshot = calculateOrderbookLevels(sellOrders, interval, true);

        // 計算買單五檔，並按價格從高到低排列
        List<OrderbookSnapshot> buySnapshot = calculateOrderbookLevels(buyOrders, interval, false);

        // 查詢買單和賣單的 ZSet 大小
        long buyOrderCount = redisTemplate.opsForZSet().zCard(symbol + ":BUY");
        long sellOrderCount = redisTemplate.opsForZSet().zCard(symbol + ":SELL");
        long totalOrderCount = buyOrderCount + sellOrderCount;

        // 結束計時
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 打印查詢時間和 ZSet 大小
        System.out.println("Orderbook snapshot query time: " + duration + " ms");
        System.out.println("BUY ZSet size: " + buyOrderCount + ", SELL ZSet size: " + sellOrderCount + ", Total: " + totalOrderCount);

        // 將結果放入 map，標記為 "buy" 和 "sell"
        Map<String, List<OrderbookSnapshot>> snapshotMap = new HashMap<>();
        snapshotMap.put("buy", buySnapshot);
        snapshotMap.put("sell", sellSnapshot);

        return snapshotMap;
    }


    private List<OrderbookSnapshot> calculateOrderbookLevels(List<Order> orders, BigDecimal interval, boolean isSell) {
        TreeMap<BigDecimal, List<Order>> groupedOrders = new TreeMap<>();

        for (Order order : orders) {
            // 調整價格區間劃分，確保價格被正確分配到對應的區間
            BigDecimal priceGroup = order.getPrice()
                    .divide(interval, 0, BigDecimal.ROUND_DOWN) // 確保價格被劃分到整數區間
                    .multiply(interval)
                    .setScale(2, BigDecimal.ROUND_HALF_UP);  // 保留兩位小數

            // 將訂單分配到對應的價格區間
            groupedOrders.computeIfAbsent(priceGroup, k -> new ArrayList<>()).add(order);
        }

        // 構建 orderbook snapshot，最多顯示五檔
        List<OrderbookSnapshot> snapshot = new ArrayList<>();

        // 對買單進行反向排序（高到低）
        if (!isSell) {
            // 如果是買單，使用 descendingMap 進行倒序排列
            for (Map.Entry<BigDecimal, List<Order>> entry : groupedOrders.descendingMap().entrySet()) {
                if (snapshot.size() >= 5) break;  // 限制為五檔
                BigDecimal price = entry.getKey();

                // 使用 BigDecimal 進行數量加總，保留小數點七位
                BigDecimal totalQuantity = entry.getValue().stream()
                        .map(Order::getUnfilledQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(7, BigDecimal.ROUND_HALF_UP);  // 保留七位小數

                snapshot.add(new OrderbookSnapshot(price, totalQuantity));
            }
        } else {
            // 如果是賣單，保持正序排列
            for (Map.Entry<BigDecimal, List<Order>> entry : groupedOrders.entrySet()) {
                if (snapshot.size() >= 5) break;  // 限制為五檔
                BigDecimal price = entry.getKey();

                // 使用 BigDecimal 進行數量加總，保留小數點七位
                BigDecimal totalQuantity = entry.getValue().stream()
                        .map(Order::getUnfilledQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(7, BigDecimal.ROUND_HALF_UP);  // 保留七位小數

                snapshot.add(new OrderbookSnapshot(price, totalQuantity));
            }
        }

        return snapshot;
    }

    // 從 Redis 中查詢訂單數據
// 從 Redis 中查詢訂單數據，isReverse 表示是否反向查詢（倒序）
    private List<Order> getOrdersFromRedis(String key, boolean isReverse) {
        ZSetOperations<String, String> zSetOperations = redisTemplate.opsForZSet();
        Set<String> orderSet;

        if (isReverse) {
            // 買單需要反向查詢，從分數高到低
            orderSet = zSetOperations.reverseRange(key, 0, -1);
        } else {
            // 賣單正常查詢，從分數低到高
            orderSet = zSetOperations.range(key, 0, -1);
        }

        if (orderSet == null) {
            return Collections.emptyList();
        }

        return orderSet.stream()
                .map(orderStr -> {
                    try {
                        return objectMapper.readValue(orderStr, Order.class);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    // OrderbookSnapshot class
    public static class OrderbookSnapshot {
        private final BigDecimal priceLevel;
        private final BigDecimal totalQuantity;  // 使用 BigDecimal 支持精確數量

        public OrderbookSnapshot(BigDecimal priceLevel, BigDecimal totalQuantity) {
            this.priceLevel = priceLevel;
            this.totalQuantity = totalQuantity;
        }

        public BigDecimal getPriceLevel() {
            return priceLevel;
        }

        public BigDecimal getTotalQuantity() {
            return totalQuantity;
        }

        @Override
        public String toString() {
            return "Price Level: " + priceLevel + ", Total Quantity: " + totalQuantity;
        }
    }
}
