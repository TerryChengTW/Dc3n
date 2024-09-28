package com.exchange.service;

import com.exchange.model.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
public class NewOrderbookService {

    private final RedisTemplate<String, Object> redisTemplate;

    public NewOrderbookService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 保存訂單到 Redis
    public void saveOrderToRedis(Order order) {
        String redisKey = order.getSymbol() + ":" + order.getSide();
        String hashKey = "order:" + order.getId();

        // 計算 ZSet 的 score
        int precision = 7;
        BigDecimal precisionFactor = BigDecimal.TEN.pow(precision);
        Instant modifiedTime = order.getModifiedAt();
        BigDecimal calculatedScore = order.getPrice().multiply(precisionFactor)
                .add((order.getSide() == Order.Side.BUY ? BigDecimal.valueOf(-1) : BigDecimal.ONE).multiply(BigDecimal.valueOf(modifiedTime.toEpochMilli())));
        double score = calculatedScore.doubleValue();

        // 構建 ZSet value，包含訂單 ID、未成交數量、modifiedAt
        String zsetValue = order.getId() + ":" + order.getUnfilledQuantity().toPlainString() + ":" + modifiedTime.toEpochMilli();

        // 構建 Hash 資料
        Map<String, Object> orderMap = buildOrderMap(order);

        // Pipeline 合併 ZSet 和 Hash 操作
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            // ZSet 操作
            connection.zSetCommands().zAdd(redisKey.getBytes(StandardCharsets.UTF_8), score, zsetValue.getBytes(StandardCharsets.UTF_8));
            // Hash 操作
            byte[] hashKeyBytes = hashKey.getBytes(StandardCharsets.UTF_8);
            for (Map.Entry<String, Object> entry : orderMap.entrySet()) {
                connection.hashCommands().hSet(hashKeyBytes, entry.getKey().getBytes(StandardCharsets.UTF_8), entry.getValue().toString().getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
    }

    // 構建 Hash 資料
    private Map<String, Object> buildOrderMap(Order order) {
        Map<String, Object> orderMap = new HashMap<>();
        orderMap.put("id", order.getId());
        orderMap.put("userId", order.getUserId());
        orderMap.put("quantity", order.getQuantity().toPlainString());
        orderMap.put("side", order.getSide().toString());
        orderMap.put("orderType", order.getOrderType().toString());
        orderMap.put("createdAt", order.getCreatedAt().toString());
        return orderMap;
    }

    // 取得 ZSet 中最高買價訂單
    public Order getHighestBuyOrder(String symbol) {
        String redisKey = symbol + ":BUY";
        Set<TypedTuple<Object>> highestBuyOrderSet = redisTemplate.opsForZSet().reverseRangeWithScores(redisKey, 0, 0);

        // 調試訊息
        System.out.println("getHighestBuyOrder - RedisKey: " + redisKey + ", Order Set: " + highestBuyOrderSet);

        return parseZSetOrder(symbol, highestBuyOrderSet, Order.Side.BUY);
    }

    // 取得 ZSet 中最低賣價訂單
    public Order getLowestSellOrder(String symbol) {
        String redisKey = symbol + ":SELL";
        Set<TypedTuple<Object>> lowestSellOrderSet = redisTemplate.opsForZSet().rangeWithScores(redisKey, 0, 0);

        // 調試訊息
        System.out.println("getLowestSellOrder - RedisKey: " + redisKey + ", Order Set: " + lowestSellOrderSet);

        return parseZSetOrder(symbol, lowestSellOrderSet, Order.Side.SELL);
    }

    // 解析 ZSet 訂單
    private Order parseZSetOrder(String symbol, Set<TypedTuple<Object>> orderSet, Order.Side side) {
        if (orderSet != null && !orderSet.isEmpty()) {
            TypedTuple<Object> order = orderSet.iterator().next();

            // 調試訊息
            System.out.println("Parsing ZSet Order - Value: " + order.getValue() + ", Score: " + order.getScore());

            String[] parsedValue = order.getValue().toString().split(":");
            String orderId = parsedValue[0];
            BigDecimal unfilledQuantity = new BigDecimal(parsedValue[1]);
            Instant modifiedAt = Instant.ofEpochMilli(Long.parseLong(parsedValue[2]));
            return new Order(orderId, null, symbol, BigDecimal.valueOf(order.getScore()), unfilledQuantity, BigDecimal.ZERO, unfilledQuantity, side, Order.OrderType.LIMIT, Order.OrderStatus.PENDING, null, null, Instant.now(), Instant.now(), modifiedAt);
        }

        // 調試訊息
        System.out.println("No Order Found in ZSet for side: " + side + " and symbol: " + symbol);

        return null;
    }

    // 從 Redis 中移除訂單
    public void removeOrderFromRedis(Order order) {
        String redisKey = order.getSymbol() + ":" + order.getSide();
        String hashKey = "order:" + order.getId();
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            connection.zSetCommands().zRem(redisKey.getBytes(StandardCharsets.UTF_8), order.getId().getBytes(StandardCharsets.UTF_8));
            connection.keyCommands().del(hashKey.getBytes(StandardCharsets.UTF_8));
            return null;
        });
    }

    // 更新訂單狀態
    public void updateOrderInRedis(Order order) {
        String redisKey = order.getSymbol() + ":" + order.getSide();
        String zsetValue = order.getId() + ":" + order.getUnfilledQuantity().toPlainString() + ":" + order.getModifiedAt().toEpochMilli();
        redisTemplate.opsForZSet().add(redisKey, zsetValue, order.getPrice().doubleValue());
    }
}
