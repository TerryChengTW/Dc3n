package com.exchange.service;

import com.exchange.model.Order;
import com.exchange.model.OrderSummary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
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

    // 取得 ZSet 中最高買價與最低賣價訂單
    public Map<String, OrderSummary> getTopBuyAndSellOrders(String symbol) {
        String buyKey = symbol + ":BUY";
        String sellKey = symbol + ":SELL";

        // 調用 pipeline 同時執行 ZSet 查詢
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            // 查詢最高買價訂單
            connection.zSetCommands().zRevRangeWithScores(buyKey.getBytes(StandardCharsets.UTF_8), 0, 0);
            // 查詢最低賣價訂單
            connection.zSetCommands().zRangeWithScores(sellKey.getBytes(StandardCharsets.UTF_8), 0, 0);
            return null;
        });

        // 分別解析最高買價和最低賣價訂單
        Set<TypedTuple<Object>> highestBuyOrderSet = (Set<TypedTuple<Object>>) results.get(0);
        Set<TypedTuple<Object>> lowestSellOrderSet = (Set<TypedTuple<Object>>) results.get(1);

        // 解析訂單
        OrderSummary highestBuyOrder = parseZSetOrder(symbol, highestBuyOrderSet, Order.Side.BUY);
        OrderSummary lowestSellOrder = parseZSetOrder(symbol, lowestSellOrderSet, Order.Side.SELL);

        // 返回最高買價和最低賣價訂單
        Map<String, OrderSummary> orders = new HashMap<>();
        orders.put("highestBuyOrder", highestBuyOrder);
        orders.put("lowestSellOrder", lowestSellOrder);

        return orders;
    }

    // 解析 ZSet 訂單，拿到價格和數量
    private OrderSummary parseZSetOrder(String symbol, Set<TypedTuple<Object>> orderSet, Order.Side side) {
        if (orderSet != null && !orderSet.isEmpty()) {
            TypedTuple<Object> order = orderSet.iterator().next();

            // 調試訊息 - 顯示 score 和 value
            System.out.println("Parsing ZSet Order - Value: " + order.getValue() + ", Score: " + order.getScore());

            // 解析 ZSet 的值
            String[] parsedValue = order.getValue().toString().split(":");
            String orderId = parsedValue[0];
            BigDecimal unfilledQuantity = new BigDecimal(parsedValue[1]);
            Instant modifiedAt = Instant.ofEpochMilli(Long.parseLong(parsedValue[2]));

            // 還原價格
            BigDecimal precisionFactor = BigDecimal.TEN.pow(7); // 與存入時的精度保持一致
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

            // 返回 OrderSummary，並保存原始的 zsetValue
            return new OrderSummary(orderId, symbol, price, unfilledQuantity, side, modifiedAt, order.getValue().toString());
        }

        return null;
    }



    // 從 Redis 移除訂單並組合完整訂單持久化到 MySQL
    public Order removeOrderAndPersistToDatabase(OrderSummary orderSummary) {
        String redisKey = orderSummary.getSymbol() + ":" + orderSummary.getSide();
        String hashKey = "order:" + orderSummary.getOrderId();

        // 從 Hash 讀取完整的訂單信息
        Map<Object, Object> orderData = redisTemplate.opsForHash().entries(hashKey);

        // 組合完整的 Order 對象
        Order order = buildOrderFromHashData(orderSummary, orderData);

        // 移除 ZSet 和 Hash，使用原始的 zsetValue 來移除
        redisTemplate.opsForZSet().remove(redisKey, orderSummary.getZsetValue());
        System.out.println("Removed Order from ZSet - ID: " + orderSummary.getOrderId());

        redisTemplate.delete(hashKey);
        System.out.println("Removed Order Hash - ID: " + orderSummary.getOrderId());

        // 持久化訂單
        // orderRepository.save(order); // 根據你的 repository 實現
        System.out.println("Persisted Order to MySQL - ID: " + order.getId());

        return order;
    }

    // 根據 OrderSummary 和 Hash 資料組合完整的 Order
    public Order buildOrderFromHashData(OrderSummary orderSummary, Map<Object, Object> orderData) {
        // 檢查 orderData 是否包含所有需要的字段
        System.out.println("Building Order from Hash Data - Order ID: " + orderSummary.getOrderId() + ", Hash Data: " + orderData);

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
                // 假設止損價格和止盈價格為 0，根據需要調整
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
        String originalZsetValue = orderSummary.getZsetValue();

        // 使用 orderSummary 的 modifiedAt 來保持原始值
        Instant modifiedTime = orderSummary.getModifiedAt();
        BigDecimal precisionFactor = BigDecimal.TEN.pow(7);

        // 重新計算 ZSet 的 score
        BigDecimal calculatedScore = orderSummary.getPrice().multiply(precisionFactor)
                .add((orderSummary.getSide() == Order.Side.BUY ? BigDecimal.valueOf(-1) : BigDecimal.ONE)
                        .multiply(BigDecimal.valueOf(modifiedTime.toEpochMilli())));
        double newScore = calculatedScore.doubleValue();

        // 更新 ZSet value，保持原始 modifiedAt
        String newZsetValue = orderSummary.getOrderId() + ":" + orderSummary.getUnfilledQuantity().toPlainString() + ":" + modifiedTime.toEpochMilli();

        // 使用 pipeline 執行移除和添加
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            connection.zSetCommands().zRem(redisKey.getBytes(StandardCharsets.UTF_8), originalZsetValue.getBytes(StandardCharsets.UTF_8));
            connection.zSetCommands().zAdd(redisKey.getBytes(StandardCharsets.UTF_8), newScore, newZsetValue.getBytes(StandardCharsets.UTF_8));
            return null;
        });

        System.out.println("Updated ZSet entry for partial match - ID: " + orderSummary.getOrderId());

        // 從 Hash 讀取完整的訂單信息
        String hashKey = "order:" + orderSummary.getOrderId();
        Map<Object, Object> orderData = redisTemplate.opsForHash().entries(hashKey);

        // 組合完整的 Order 對象
        Order partiallyFilledOrder = buildOrderFromHashData(orderSummary, orderData);

        // 持久化部分匹配訂單
        // orderRepository.save(partiallyFilledOrder); // 根據你的 repository 實現
        System.out.println("Persisted Partially Filled Order to MySQL - ID: " + partiallyFilledOrder.getId());

        return partiallyFilledOrder;
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