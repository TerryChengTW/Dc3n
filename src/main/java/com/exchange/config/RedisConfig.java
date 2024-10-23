package com.exchange.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // ZSet 使用 String 序列化器
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // Key 序列化為字串
        template.setKeySerializer(stringSerializer);

        // Value 和 HashKey 使用 String 序列化器
        template.setValueSerializer(stringSerializer); // ZSet 存取用 String
        template.setHashKeySerializer(stringSerializer); // HashKey 存取用 String
        template.setHashValueSerializer(stringSerializer); // **將 HashValue 改為 String 序列化**

        return template;
    }
}
