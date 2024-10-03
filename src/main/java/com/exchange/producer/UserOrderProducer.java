package com.exchange.producer;

import com.exchange.dto.OrderDTO;
import com.exchange.model.Order;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class UserOrderProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public UserOrderProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendOrderUpdate(Order order) throws JsonProcessingException {
        OrderDTO orderDTO = convertToDTO(order);
        String orderJson = objectMapper.writeValueAsString(orderDTO);
        kafkaTemplate.send("user-order-updates", order.getUserId(), orderJson);
    }


    private OrderDTO convertToDTO(Order order) {
        OrderDTO dto = new OrderDTO();
        dto.setId(order.getId());
        dto.setUserId(order.getUserId());
        dto.setSymbol(order.getSymbol());
        dto.setPrice(order.getPrice());
        dto.setQuantity(order.getQuantity());
        dto.setFilledQuantity(order.getFilledQuantity());
        dto.setUnfilledQuantity(order.getUnfilledQuantity());
        dto.setSide(order.getSide().toString());
        dto.setOrderType(order.getOrderType().toString());
        dto.setStatus(order.getStatus().toString());

        // Convert Instant to ZonedDateTime using UTC
        dto.setCreatedAt(ZonedDateTime.ofInstant(order.getCreatedAt(), ZoneId.of("UTC+8")));
        dto.setUpdatedAt(ZonedDateTime.ofInstant(order.getUpdatedAt(), ZoneId.of("UTC+8")));
        dto.setModifiedAt(ZonedDateTime.ofInstant(order.getModifiedAt(), ZoneId.of("UTC+8")));

        return dto;
    }

}