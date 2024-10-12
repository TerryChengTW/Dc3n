package com.exchange.consumer;

import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderBookDeltaSubscriptionManager {

    private final ConsumerFactory<String, String> consumerFactory;
    private final Map<String, KafkaMessageListenerContainer<String, String>> topicContainers = new HashMap<>();

    public OrderBookDeltaSubscriptionManager(ConsumerFactory<String, String> consumerFactory) {
        // 使用帶有 "latest" offset 策略的 ConsumerFactory
        Map<String, Object> config = new HashMap<>(consumerFactory.getConfigurationProperties());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, OffsetResetStrategy.LATEST.name().toLowerCase());
        this.consumerFactory = new DefaultKafkaConsumerFactory<>(config);
    }

    public void subscribeToSymbol(String symbol, MessageListener<String, String> messageListener) {
        String topic = "order-book-delta-" + symbol.toLowerCase();

        if (!topicContainers.containsKey(topic)) {
            // 動態生成唯一的 groupId
            String dynamicGroupId = "order-book-group-" + UUID.randomUUID();

            ContainerProperties containerProps = new ContainerProperties(topic);
            containerProps.setMessageListener(messageListener);

            // 配置 Kafka 消費者工廠，使用動態的 groupId
            Map<String, Object> consumerConfig = new HashMap<>(consumerFactory.getConfigurationProperties());
            consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, dynamicGroupId);

            ConsumerFactory<String, String> dynamicConsumerFactory = new DefaultKafkaConsumerFactory<>(consumerConfig);

            // Create a new Kafka listener container with the dynamic consumer factory
            KafkaMessageListenerContainer<String, String> container = new KafkaMessageListenerContainer<>(dynamicConsumerFactory, containerProps);
            container.start();
            topicContainers.put(topic, container);
        }
    }
}
