//package com.exchange.service;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.stereotype.Service;
//
//@Service
//public class RedisTestService {
//
//    @Autowired
//    private RedisTemplate<String, Object> redisTemplate;
//
//    public void testRedis() {
//        redisTemplate.opsForValue().set("testKey", "testValue");
//        String value = (String) redisTemplate.opsForValue().get("testKey");
//        System.out.println("Value from Redis: " + value);
//    }
//}
