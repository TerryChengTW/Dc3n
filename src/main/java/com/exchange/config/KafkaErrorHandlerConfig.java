package com.exchange.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.util.backoff.FixedBackOff;

import java.util.function.BiConsumer;

@Configuration
public class KafkaErrorHandlerConfig {

    @Bean
    public DefaultErrorHandler errorHandler() {
        // 自定義錯誤處理，僅打印簡單消息
        BiConsumer<ConsumerRecord<?, ?>, Exception> recoverer = (record, ex) -> {
            System.out.println("Error processing record: " + record.value() + ", Error: " + ex.getMessage());
        };

        // 使用 FixedBackOff，表示不重試
        FixedBackOff fixedBackOff = new FixedBackOff(0L, 0L);

        // 返回自定義的 DefaultErrorHandler
        return new DefaultErrorHandler(recoverer::accept, fixedBackOff);
    }
}

