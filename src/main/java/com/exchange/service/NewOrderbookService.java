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
    private long totalInsertTime = 0; // 總插入時間（納秒）
    private long insertCount = 0;     // 插入次數

    public NewOrderbookService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 將訂單保存到 Redis
    public void saveOrderToRedis(Order order) {
        String redisKey = order.getSymbol() + ":" + order.getSide();
        String hashKey = "order:" + order.getId();
        double score = order.getPrice().doubleValue();

        // 構建 Hash 資料
        Map<String, Object> orderMap = buildOrderMap(order);

        // 開始記錄時間
        long startTime = System.nanoTime();

        // 使用 Pipeline 合併 ZSet 和 Hash 操作
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            // ZSet 操作
            connection.zSetCommands().zAdd(redisKey.getBytes(StandardCharsets.UTF_8), score, order.getId().getBytes(StandardCharsets.UTF_8));

            // Hash 操作
            byte[] hashKeyBytes = hashKey.getBytes(StandardCharsets.UTF_8);
            for (Map.Entry<String, Object> entry : orderMap.entrySet()) {
                connection.hashCommands().hSet(
                        hashKeyBytes,
                        entry.getKey().getBytes(StandardCharsets.UTF_8),
                        entry.getValue().toString().getBytes(StandardCharsets.UTF_8)
                );
            }

            return null;
        });

        // 記錄結束時間並計算耗時
        long endTime = System.nanoTime();
        long insertTime = endTime - startTime; // 單次插入耗時
        totalInsertTime += insertTime; // 累積總插入時間
        insertCount++; // 插入次數增加

        // 計算平均插入時間並打印
        if (insertCount % 1000 == 0) { // 每 1000 次打印一次平均時間，可根據需求調整
            double avgInsertTimeMs = (double) totalInsertTime / insertCount / 1_000_000;
            System.out.println("Average insert time after " + insertCount + " inserts (ms): " + avgInsertTimeMs);
        }
    }

    // 從 Redis 中獲取訂單
    public Order getOrderFromRedis(String orderId) {
        Map<Object, Object> orderData = redisTemplate.opsForHash().entries("order:" + orderId);
        return mapToOrder(orderData);
    }

    // 獲取最高買價訂單
    public Order getHighestBuyOrder(String symbol) {
        String redisKey = symbol + ":BUY";
        Set<TypedTuple<Object>> highestBuyOrder = redisTemplate.opsForZSet().reverseRangeWithScores(redisKey, 0, 0);
        if (highestBuyOrder != null && !highestBuyOrder.isEmpty()) {
            String orderId = (String) highestBuyOrder.iterator().next().getValue();
            return getOrderFromRedis(orderId);
        }
        return null;
    }

    // 獲取最低賣價訂單
    public Order getLowestSellOrder(String symbol) {
        String redisKey = symbol + ":SELL";
        Set<TypedTuple<Object>> lowestSellOrder = redisTemplate.opsForZSet().rangeWithScores(redisKey, 0, 0);
        if (lowestSellOrder != null && !lowestSellOrder.isEmpty()) {
            String orderId = (String) lowestSellOrder.iterator().next().getValue();
            return getOrderFromRedis(orderId);
        }
        return null;
    }

    // 更新訂單在 Redis 中的狀態
    public void updateOrderInRedis(Order order) {
        String hashKey = "order:" + order.getId();
        Map<String, Object> orderMap = buildOrderMap(order);

        // 使用 Pipeline 合併 Hash 操作
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] hashKeyBytes = hashKey.getBytes(StandardCharsets.UTF_8);
            for (Map.Entry<String, Object> entry : orderMap.entrySet()) {
                connection.hashCommands().hSet(
                        hashKeyBytes,
                        entry.getKey().getBytes(StandardCharsets.UTF_8),
                        entry.getValue().toString().getBytes(StandardCharsets.UTF_8)
                );
            }
            return null;
        });
    }

    // 從 Redis 中移除已完全成交的訂單
    public void removeOrderFromRedis(Order order) {
        String redisKey = order.getSymbol() + ":" + order.getSide();
        String hashKey = "order:" + order.getId();

        // 使用 Pipeline 刪除 ZSet 和 Hash
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            connection.zSetCommands().zRem(redisKey.getBytes(StandardCharsets.UTF_8), order.getId().getBytes(StandardCharsets.UTF_8));
            connection.keyCommands().del(hashKey.getBytes(StandardCharsets.UTF_8));
            return null;
        });
    }

    // Helper 方法：構建 Hash 資料
    private Map<String, Object> buildOrderMap(Order order) {
        Map<String, Object> orderMap = new HashMap<>(); 
        orderMap.put("id", order.getId());
        orderMap.put("userId", order.getUserId());
        orderMap.put("symbol", order.getSymbol());
        orderMap.put("price", order.getPrice().toPlainString());
        orderMap.put("quantity", order.getQuantity().toPlainString());
        orderMap.put("filledQuantity", order.getFilledQuantity().toPlainString());
        orderMap.put("unfilledQuantity", order.getUnfilledQuantity().toPlainString());
        orderMap.put("side", order.getSide().toString());
        orderMap.put("orderType", order.getOrderType().toString());
        orderMap.put("status", order.getStatus().toString());
        orderMap.put("createdAt", order.getCreatedAt().toString());
        orderMap.put("updatedAt", order.getUpdatedAt().toString());
        orderMap.put("modifiedAt", order.getModifiedAt().toString());
        return orderMap;
    }

    // Helper 方法：將 Hash 資料映射回 Order 對象
    private Order mapToOrder(Map<Object, Object> orderData) {
        try {
            // 提取並轉換 Hash 資料
            String id = (String) orderData.get("id");
            String userId = (String) orderData.get("userId");
            String symbol = (String) orderData.get("symbol");
            BigDecimal price = new BigDecimal((String) orderData.get("price"));
            BigDecimal quantity = new BigDecimal((String) orderData.get("quantity"));
            BigDecimal filledQuantity = new BigDecimal((String) orderData.get("filledQuantity"));
            BigDecimal unfilledQuantity = new BigDecimal((String) orderData.get("unfilledQuantity"));
            Order.Side side = Order.Side.valueOf((String) orderData.get("side"));
            Order.OrderType orderType = Order.OrderType.valueOf((String) orderData.get("orderType"));
            Order.OrderStatus status = Order.OrderStatus.valueOf((String) orderData.get("status"));
            Instant createdAt = Instant.parse((String) orderData.get("createdAt"));
            Instant updatedAt = Instant.parse((String) orderData.get("updatedAt"));
            Instant modifiedAt = Instant.parse((String) orderData.get("modifiedAt"));

            // 處理可能為空的字段
            BigDecimal stopPrice = orderData.get("stopPrice") != null ? new BigDecimal((String) orderData.get("stopPrice")) : null;
            BigDecimal takeProfitPrice = orderData.get("takeProfitPrice") != null ? new BigDecimal((String) orderData.get("takeProfitPrice")) : null;

            // 創建 Order 對象
            return new Order(
                    id,
                    userId,
                    symbol,
                    price,
                    quantity,
                    filledQuantity,
                    unfilledQuantity,
                    side,
                    orderType,
                    status,
                    stopPrice,
                    takeProfitPrice,
                    createdAt,
                    updatedAt,
                    modifiedAt,
                    null,  // buyTrades 這裡暫時設置為 null，因為從 Redis 中無法獲取 Trade 資料
                    null   // sellTrades 這裡暫時設置為 null，因為從 Redis 中無法獲取 Trade 資料
            );
        } catch (Exception e) {
            // 打印錯誤日志並返回 null，或根據實際情況拋出自定義異常
            System.err.println("Error mapping order data: " + e.getMessage());
            return null;
        }
    }

}
