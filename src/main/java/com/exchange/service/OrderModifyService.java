package com.exchange.service;

import com.exchange.model.Order;
import com.exchange.utils.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@Service
public class OrderModifyService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public OrderModifyService(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public ResponseEntity<ApiResponse<?>> checkAndRemoveOrderFromRedis(String symbol, String side, String orderId, BigDecimal price, long modifiedAt, String userId) {
        try {
            // 計算 ZSet 的 score
            double score = calculateScore(price, modifiedAt, side);

            // 生成 Redis 的 key (根據 symbol 和 side)
            String redisKey = getRedisKey(symbol, side);

            // 查詢 ZSet 中該 score 對應的所有訂單
            Set<Object> orders = redisTemplate.opsForZSet().rangeByScore(redisKey, score, score);

            // 如果查詢結果為空，表示訂單不存在
            if (orders == null || orders.isEmpty()) {
                return ResponseEntity.badRequest().body(new ApiResponse<>("訂單未找到", "40401"));
            }

            // 遍歷查詢到的訂單，確認 orderId 和 userId
            for (Object orderObj : orders) {
                String orderJson = (String) orderObj; // 假設你儲存的是 JSON 格式的訂單
                // 提取 value 中的訂單資料
                Order order = objectMapper.readValue(orderJson, Order.class);

                // 檢查 orderId 和 userId 是否符合
                if (order.getId().equals(orderId) && order.getUserId().equals(userId)) {
                    // 找到對應訂單，移除 ZSet 中的該訂單
                    redisTemplate.opsForZSet().remove(redisKey, orderJson);
                    return ResponseEntity.ok(new ApiResponse<>("訂單查詢並移除成功", order));
                }
            }

            // 如果沒有找到符合條件的訂單
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>("無權操作該訂單", "40302"));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse<>("處理訂單時發生錯誤", "50001"));
        }
    }

    // 計算 ZSet 的 score
    public double calculateScore(BigDecimal price, long modifiedAt, String side) {
        int precision = 7; // 假設使用 7 位精度
        BigDecimal precisionFactor = BigDecimal.TEN.pow(precision);
        BigDecimal pricePart = price.multiply(precisionFactor);
        BigDecimal timePart = BigDecimal.valueOf(modifiedAt);
        BigDecimal calculatedScore = side.equalsIgnoreCase("BUY")
                ? pricePart.subtract(timePart)
                : pricePart.add(timePart);
        return calculatedScore.doubleValue();
    }

    // 根據 symbol 和 side 生成 Redis 的 key
    public String getRedisKey(String symbol, String side) {
        return symbol + ":" + side;
    }
}
