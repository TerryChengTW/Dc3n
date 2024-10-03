package com.exchange.repository;

import com.exchange.dto.OrderDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
public class RedisOrderRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public RedisOrderRepository(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public List<OrderDTO> getUserOrders(String userId) {
        List<OrderDTO> orders = new ArrayList<>();
        String[] symbols = {"BTCUSDT"}; // 可以擴展到更多交易對
        String[] sides = {"BUY", "SELL"};

        for (String symbol : symbols) {
            for (String side : sides) {
                String key = symbol + ":" + side;
                Set<String> orderSet = redisTemplate.opsForZSet().range(key, 0, -1);

                if (orderSet != null) {
                    for (String orderJson : orderSet) {
                        try {
                            OrderDTO order = objectMapper.readValue(orderJson, OrderDTO.class);
                            if (order.getUserId().equals(userId)) {
                                orders.add(order);
                            }
                        } catch (JsonProcessingException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        return orders;
    }
}