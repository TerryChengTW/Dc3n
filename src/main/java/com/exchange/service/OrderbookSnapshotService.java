package com.exchange.service;

import com.exchange.model.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
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

        // 默認的價格區間為 100，如果傳入為 null 則使用默認值
        BigDecimal priceInterval = (interval != null) ? interval : BigDecimal.valueOf(100);

        // 取得買單和賣單第一檔，檢查返回的結果是否為空
        ZSetOperations.TypedTuple<String> firstBuyOrder = fetchFirstOrder(symbol, ":BUY", true);
        ZSetOperations.TypedTuple<String> firstSellOrder = fetchFirstOrder(symbol, ":SELL", false);

        if (firstBuyOrder == null || firstSellOrder == null) {
            // 如果任何一方的訂單為空，返回空快照
            return Collections.emptyMap();
        }

        // 根據第一檔訂單解析價格
        BigDecimal buyPrice = parseOrderPrice(firstBuyOrder).orElse(BigDecimal.ZERO);
        BigDecimal sellPrice = parseOrderPrice(firstSellOrder).orElse(BigDecimal.ZERO);

        // 確定 score 範圍
        double[] buyScoreRange = calculateScoreRange(buyPrice, priceInterval, true);
        double[] sellScoreRange = calculateScoreRange(sellPrice, priceInterval, false);

        // Lua腳本查詢
        String luaScript =
                "local buyOrders = redis.call('ZRANGEBYSCORE', KEYS[1], ARGV[1], ARGV[2], 'WITHSCORES') " +
                        "local sellOrders = redis.call('ZRANGEBYSCORE', KEYS[2], ARGV[3], ARGV[4], 'WITHSCORES') " +
                        "return {buyOrders, sellOrders}";

        // 構建參數
        List<String> keys = Arrays.asList(symbol + ":BUY", symbol + ":SELL");
        List<String> args = Arrays.asList(
                String.valueOf(buyScoreRange[0]), String.valueOf(buyScoreRange[1]),
                String.valueOf(sellScoreRange[0]), String.valueOf(sellScoreRange[1])
        );

        // 執行Lua腳本
        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>(luaScript, List.class);
        List<Object> result = redisTemplate.execute(redisScript, keys, args.toArray());

        // 解析買單和賣單的結果
        Set<ZSetOperations.TypedTuple<String>> buyOrders = parseRedisResult((List<Object>) result.get(0));
        Set<ZSetOperations.TypedTuple<String>> sellOrders = parseRedisResult((List<Object>) result.get(1));

        // 聚合買單和賣單快照
        Map<BigDecimal, BigDecimal> buySnapshot = aggregateOrderSnapshot(buyOrders, priceInterval, true);
        Map<BigDecimal, BigDecimal> sellSnapshot = aggregateOrderSnapshot(sellOrders, priceInterval, false);

        // 組裝最終快照，取前50檔
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("buy", getTopNOrders(buySnapshot, 50, true));
        snapshot.put("sell", getTopNOrders(sellSnapshot, 50, false));

        long endTime = System.currentTimeMillis(); // 結束計時
        long duration = endTime - startTime; // 計算執行時間
        System.out.println("執行時間: " + duration + " 毫秒");

        return snapshot;
    }

    // 解析Redis查詢結果
    private Set<ZSetOperations.TypedTuple<String>> parseRedisResult(List<Object> redisResult) {
        Set<ZSetOperations.TypedTuple<String>> orders = new HashSet<>();
        if (redisResult == null || redisResult.isEmpty()) {
            return orders;
        }
        for (int i = 0; i < redisResult.size(); i += 2) {
            String value = (String) redisResult.get(i);
            Double score = Double.parseDouble(redisResult.get(i + 1).toString());
            orders.add(new DefaultTypedTuple<>(value, score));
        }
        return orders;
    }

    private ZSetOperations.TypedTuple<String> fetchFirstOrder(String symbol, String type, boolean isBuy) {
        Set<ZSetOperations.TypedTuple<String>> orders = isBuy
                ? redisTemplate.opsForZSet().reverseRangeWithScores(symbol + type, 0, 0)
                : redisTemplate.opsForZSet().rangeWithScores(symbol + type, 0, 0);

        return (orders == null || orders.isEmpty()) ? null : orders.iterator().next();
    }

    private Optional<BigDecimal> parseOrderPrice(ZSetOperations.TypedTuple<String> orderTuple) {
        try {
            Order order = objectMapper.readValue(orderTuple.getValue(), Order.class);
            return Optional.of(order.getPrice());
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    private double[] calculateScoreRange(BigDecimal price, BigDecimal priceInterval, boolean isBuy) {
        double rangeMultiplier = 50; // 例如，擴大到 25 倍區間，兩邊加總50檔
        double minScore = calculateScore(price.subtract(priceInterval.multiply(BigDecimal.valueOf(rangeMultiplier))), priceInterval, isBuy, true);
        double maxScore = calculateScore(price.add(priceInterval.multiply(BigDecimal.valueOf(rangeMultiplier))), priceInterval, isBuy, false);
        return new double[]{minScore, maxScore};
    }

    private double calculateScore(BigDecimal price, BigDecimal priceInterval, boolean isBuy, boolean isLowerBound) {
        int precision = 7;
        BigDecimal precisionFactor = BigDecimal.TEN.pow(precision);

        // 確定價格區間
        BigDecimal adjustedPrice = (isLowerBound ? price.divide(priceInterval, 0, BigDecimal.ROUND_FLOOR)
                : price.divide(priceInterval, 0, BigDecimal.ROUND_CEILING)).multiply(priceInterval);

        // 設定時間戳
        Instant timestamp = (isBuy && isLowerBound) || (!isBuy && !isLowerBound) ? Instant.now() : Instant.ofEpochMilli(0);

        // 根據買賣方向調整時間權重
        BigDecimal calculatedScore = adjustedPrice.multiply(precisionFactor)
                .add((isBuy ? BigDecimal.valueOf(-1) : BigDecimal.ONE).multiply(BigDecimal.valueOf(timestamp.toEpochMilli())));
        return calculatedScore.doubleValue();
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

                    // 打印對應價格和價格區間
//                    System.out.println("Order Price: " + orderPrice + ", Interval Price: " + intervalPrice);

                    // 聚合對應價格區間的訂單數量
                    snapshot.merge(intervalPrice, orderQuantity, BigDecimal::add);
                } catch (Exception e) {
                    e.printStackTrace(); // 處理解析異常
                }
            }
        }

        return snapshot;
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