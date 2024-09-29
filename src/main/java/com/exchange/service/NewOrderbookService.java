package com.exchange.service;

import com.exchange.model.Order;
import com.exchange.utils.SnowflakeIdGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisCallback;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class NewOrderbookService {

    private final RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper;


    public NewOrderbookService(RedisTemplate<String, Object> redisTemplate, KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, SnowflakeIdGenerator snowflakeIdGenerator, ObjectMapper objectMapper1) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper1;
    }

    public void saveOrderToRedis(Order order) {
        String redisKey = order.getSymbol() + ":" + order.getSide();

        // 計算 ZSet 的 score
        int precision = 7;
        BigDecimal precisionFactor = BigDecimal.TEN.pow(precision);
        Instant modifiedTime = order.getModifiedAt();
        BigDecimal calculatedScore = order.getPrice().multiply(precisionFactor)
                .add((order.getSide() == Order.Side.BUY ? BigDecimal.valueOf(-1) : BigDecimal.ONE).multiply(BigDecimal.valueOf(modifiedTime.toEpochMilli())));
        double score = calculatedScore.doubleValue();

        // 將訂單轉換為 JSON 字符串
        String jsonString = null;
        try {
            jsonString = objectMapper.writeValueAsString(order);
        } catch (JsonProcessingException e) {
            System.err.println("Error: Failed to convert order to JSON. Order: " + order);
            e.printStackTrace();
            // 你可以在這裡選擇拋出異常或者進行其他錯誤處理
            throw new RuntimeException("Failed to convert order to JSON", e);
        }

        // 使用 ZSet 儲存完整的訂單 JSON
        String finalJsonString = jsonString;
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            // ZSet 操作，將完整 JSON 字符串作為 value
            connection.zSetCommands().zAdd(redisKey.getBytes(StandardCharsets.UTF_8), score, finalJsonString.getBytes(StandardCharsets.UTF_8));
            return null;
        });
    }

}