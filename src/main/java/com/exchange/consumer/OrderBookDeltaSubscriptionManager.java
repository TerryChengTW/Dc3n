package com.exchange.consumer;

import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class OrderBookDeltaSubscriptionManager {

    private final ConsumerFactory<String, String> consumerFactory;
    private final Map<String, KafkaMessageListenerContainer<String, String>> topicContainers = new HashMap<>();

    public OrderBookDeltaSubscriptionManager(ConsumerFactory<String, String> consumerFactory) {
        this.consumerFactory = consumerFactory;
    }

    public void subscribeToSymbol(String symbol, MessageListener<String, String> messageListener) {
        String topic = "order-book-delta-" + symbol.toLowerCase();

        if (!topicContainers.containsKey(topic)) {
            ContainerProperties containerProps = new ContainerProperties(topic);
            containerProps.setMessageListener(messageListener);

            // Create a new Kafka listener container
            KafkaMessageListenerContainer<String, String> container = new KafkaMessageListenerContainer<>(consumerFactory, containerProps);
            container.start();
            topicContainers.put(topic, container);
        }
    }

    public void unsubscribeFromSymbol(String symbol) {
        String topic = "order-book-delta-" + symbol.toLowerCase();
        KafkaMessageListenerContainer<String, String> container = topicContainers.remove(topic);

        if (container != null) {
            // Stop and remove the Kafka listener container
            container.stop();
        }
    }
}
