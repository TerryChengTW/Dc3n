package com.exchange.producer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderBookDeltaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.deltas.topic.prefix:order-book-delta-}")
    private String topicPrefix;

    public OrderBookDeltaProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendDelta(String symbol, String side, String price, String unfilledQuantity) {
        String topic = topicPrefix + symbol.toLowerCase();
        String deltaMessage = String.format("{\"symbol\":\"%s\",\"side\":\"%s\",\"price\":\"%s\",\"unfilledQuantity\":\"%s\"}",
                symbol, side, price, unfilledQuantity);

        kafkaTemplate.send(topic, deltaMessage);
//        System.out.println("Sent delta message to topic " + topic + ": " + deltaMessage);
    }
}
