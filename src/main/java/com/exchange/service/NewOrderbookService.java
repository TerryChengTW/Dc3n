package com.exchange.service;

import com.exchange.model.Order;
import com.exchange.model.OrderSummary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

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

    public Map<String, OrderSummary> getTopBuyAndSellOrders(String symbol) {

        String buyKey = symbol + ":BUY";
        String sellKey = symbol + ":SELL";

        // 調用 pipeline 同時執行 ZSet 查詢
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            connection.zSetCommands().zRevRangeWithScores(buyKey.getBytes(StandardCharsets.UTF_8), 0, 0);
            connection.zSetCommands().zRangeWithScores(sellKey.getBytes(StandardCharsets.UTF_8), 0, 0);
            return null;
        });

        // 分別解析最高買價和最低賣價訂單
        Set<TypedTuple<Object>> highestBuyOrderSet = (Set<TypedTuple<Object>>) results.get(0);
        Set<TypedTuple<Object>> lowestSellOrderSet = (Set<TypedTuple<Object>>) results.get(1);

        OrderSummary highestBuyOrder = parseZSetOrder(symbol, highestBuyOrderSet, Order.Side.BUY);
        OrderSummary lowestSellOrder = parseZSetOrder(symbol, lowestSellOrderSet, Order.Side.SELL);

        // 返回最高買價和最低賣價訂單
        Map<String, OrderSummary> orders = new HashMap<>();
        orders.put("highestBuyOrder", highestBuyOrder);
        orders.put("lowestSellOrder", lowestSellOrder);

        return orders;
    }

    private OrderSummary parseZSetOrder(String symbol, Set<TypedTuple<Object>> orderSet, Order.Side side) {
        if (orderSet != null && !orderSet.isEmpty()) {
            TypedTuple<Object> order = orderSet.iterator().next();

            // 解析 ZSet 的值
            String[] parsedValue = order.getValue().toString().split(":");
            String orderId = parsedValue[0];
            BigDecimal unfilledQuantity = new BigDecimal(parsedValue[1]);
            Instant modifiedAt = Instant.ofEpochMilli(Long.parseLong(parsedValue[2]));

            // 還原價格
            BigDecimal precisionFactor = BigDecimal.TEN.pow(7);
            BigDecimal score = BigDecimal.valueOf(order.getScore());

            // 計算時間戳部分
            BigDecimal timestampPart = BigDecimal.valueOf(modifiedAt.toEpochMilli());

            // 根據買賣方向還原原始價格
            BigDecimal price;
            if (side == Order.Side.BUY) {
                price = score.add(timestampPart).divide(precisionFactor, RoundingMode.HALF_UP);
            } else {
                price = score.subtract(timestampPart).divide(precisionFactor, RoundingMode.HALF_UP);
            }

            return new OrderSummary(orderId, symbol, price, unfilledQuantity, side, modifiedAt, order.getValue().toString());
        }

        return null;
    }

    // 從 Redis 移除訂單並組合完整訂單持久化到 MySQL
    public Order removeOrderAndPersistToDatabase(OrderSummary orderSummary) {
        String redisKey = orderSummary.getSymbol() + ":" + orderSummary.getSide();
        String hashKey = "order:" + orderSummary.getOrderId();

        // 使用 pipeline 執行 Hash 讀取和 ZSet/Hash 移除操作
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            // 讀取 Hash 值
            connection.hashCommands().hGetAll(hashKey.getBytes(StandardCharsets.UTF_8));
            // 移除 ZSet 值
            connection.zSetCommands().zRem(redisKey.getBytes(StandardCharsets.UTF_8), orderSummary.getZsetValue().getBytes(StandardCharsets.UTF_8));
            // 刪除 Hash
            connection.keyCommands().del(hashKey.getBytes(StandardCharsets.UTF_8));
            return null;
        });

        // 從 pipeline 返回的結果中取得 Hash 讀取結果
        @SuppressWarnings("unchecked")
        Map<Object, Object> orderData = (Map<Object, Object>) results.get(0);

        // 組合完整的 Order 對象
        Order order = buildOrderFromHashData(orderSummary, orderData);

        return order;
    }


    // 根據 OrderSummary 和 Hash 資料組合完整的 Order
    public Order buildOrderFromHashData(OrderSummary orderSummary, Map<Object, Object> orderData) {
        // 從 Hash 資料中取得原始下單數量
        BigDecimal quantity = new BigDecimal((String) orderData.get("quantity"));
        // 根據原始數量和未成交數量計算已成交數量
        BigDecimal filledQuantity = quantity.subtract(orderSummary.getUnfilledQuantity());

        // 判斷訂單狀態
        Order.OrderStatus orderStatus = orderSummary.getUnfilledQuantity().compareTo(BigDecimal.ZERO) == 0
                ? Order.OrderStatus.COMPLETED
                : Order.OrderStatus.PARTIALLY_FILLED;

        // 使用 Hash 資料構建完整的 Order 對象
        return new Order(
                (String) orderData.get("id"),
                (String) orderData.get("userId"),
                orderSummary.getSymbol(),
                orderSummary.getPrice(),
                quantity, // 原始下單數量
                filledQuantity, // 已成交數量
                orderSummary.getUnfilledQuantity(), // 未成交數量
                orderSummary.getSide(),
                Order.OrderType.valueOf((String) orderData.get("orderType")),
                orderStatus, // 設定訂單狀態為 COMPLETED 或 PARTIALLY_FILLED
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Instant.parse((String) orderData.get("createdAt")),
                Instant.now(), // 更新 updatedAt
                orderSummary.getModifiedAt() // 使用 ZSet 中的原始 modifiedAt
        );
    }

    // 更新部分匹配訂單的 ZSet 並持久化到 MySQL
    public Order updateOrderInZSetAndPersistToDatabase(OrderSummary orderSummary) {
        String redisKey = orderSummary.getSymbol() + ":" + orderSummary.getSide();
        String hashKey = "order:" + orderSummary.getOrderId();

        // 保留原有的 BigDecimal 計算
        BigDecimal precisionFactor = BigDecimal.TEN.pow(7);
        BigDecimal calculatedScore = orderSummary.getPrice().multiply(precisionFactor)
                .add((orderSummary.getSide() == Order.Side.BUY ? BigDecimal.valueOf(-1) : BigDecimal.ONE)
                        .multiply(BigDecimal.valueOf(orderSummary.getModifiedAt().toEpochMilli())));
        double newScore = calculatedScore.doubleValue();

        String newZsetValue = orderSummary.getOrderId() + ":" + orderSummary.getUnfilledQuantity().toPlainString() + ":" + orderSummary.getModifiedAt().toEpochMilli();

        // 使用 Lua 腳本一次性完成 ZSet 更新和 Hash 讀取
        String script = "redis.call('zrem', KEYS[1], ARGV[1]); " +
                "redis.call('zadd', KEYS[1], ARGV[2], ARGV[3]); " +
                "return redis.call('hgetall', KEYS[2]);";

        List<Object> result = redisTemplate.execute(new DefaultRedisScript<>(script, List.class),
                Arrays.asList(redisKey, hashKey),
                orderSummary.getZsetValue(), String.valueOf(newScore), newZsetValue);

        // 解析 Lua 腳本返回的 Hash 數據
        Map<Object, Object> orderData = new HashMap<>();
        for (int i = 0; i < result.size(); i += 2) {
            orderData.put(result.get(i), result.get(i + 1));
        }

        // 異步處理對象轉換和持久化
        CompletableFuture.runAsync(() -> {
            Order partiallyFilledOrder = buildOrderFromHashData(orderSummary, orderData);
            // TODO: 持久化到 MySQL
        });

        // 如果需要立即返回 Order 對象，在這裡同步構建
        return buildOrderFromHashData(orderSummary, orderData);
    }

    // 當用戶提交更新訂單時，同時更新 ZSet 和 Hash
    public void updateOrderInRedis(Order order) {
        String redisKey = order.getSymbol() + ":" + order.getSide();
        String hashKey = "order:" + order.getId();

        // 更新 Hash，覆蓋用戶提交的變動屬性
        Map<String, Object> orderMap = buildOrderMap(order);
        redisTemplate.opsForHash().putAll(hashKey, orderMap);

        // 重新計算 ZSet 的 score
        BigDecimal precisionFactor = BigDecimal.TEN.pow(7);
        Instant modifiedTime = order.getModifiedAt();
        BigDecimal calculatedScore = order.getPrice().multiply(precisionFactor)
                .add((order.getSide() == Order.Side.BUY ? BigDecimal.valueOf(-1) : BigDecimal.ONE).multiply(BigDecimal.valueOf(modifiedTime.toEpochMilli())));
        double score = calculatedScore.doubleValue();

        // 更新 ZSet value，包含新的 unfilledQuantity 和 modifiedAt
        String zsetValue = order.getId() + ":" + order.getUnfilledQuantity().toPlainString() + ":" + modifiedTime.toEpochMilli();
        redisTemplate.opsForZSet().add(redisKey, zsetValue, score);
    }
}