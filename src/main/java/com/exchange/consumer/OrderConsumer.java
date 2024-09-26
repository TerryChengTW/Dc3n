package com.exchange.consumer;

import com.exchange.model.Order;
import com.exchange.service.NewOrderMatchingService;
import com.exchange.service.NewOrderbookService;
import com.exchange.service.OrderMatchingService;
import com.exchange.service.OrderUpdateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class OrderConsumer {

    private final OrderMatchingService matchingService;
    private final OrderUpdateService updateService;
    private final ObjectMapper objectMapper;
    private final NewOrderbookService newOrderbookService;
    private final NewOrderMatchingService newMatchingService;


    public OrderConsumer(OrderMatchingService matchingService,
                         OrderUpdateService updateService,
                         ObjectMapper objectMapper,
                         NewOrderbookService newOrderbookService,
                         NewOrderMatchingService newMatchingService) {
        this.matchingService = matchingService;
        this.updateService = updateService;
        this.objectMapper = objectMapper;
        this.newOrderbookService = newOrderbookService;
        this.newMatchingService = newMatchingService;
    }

    // 消費新訂單
    @KafkaListener(topics = "new_orders", groupId = "order_group")
    public void consumeNewOrder(String orderJson) {
        try {
            // 將接收到的 JSON 訂單轉換為 Order 對象
            Order order = objectMapper.readValue(orderJson, Order.class);

            // 根據訂單類型進行不同處理
            if (order.getOrderType() == Order.OrderType.MARKET) {
                // 市場單立即送至撮合服務處理
                newMatchingService.processMarketOrder(order);
            } else if (order.getOrderType() == Order.OrderType.LIMIT) {
                // 限價單保存到 Redis
                newOrderbookService.saveOrderToRedis(order);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 消費更新訂單
    @KafkaListener(topics = "update_orders", groupId = "order_group")
    public void consumeUpdateOrder(String orderJson) {
        try {
            // 將接收到的 JSON 訂單轉換為 Order 對象
            Order order = objectMapper.readValue(orderJson, Order.class);

            // 更新訂單到 Redis 和 MySQL
            updateService.updateOrderInRedis(order);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 消費取消訂單
    @KafkaListener(topics = "cancel_orders", groupId = "order_group")
    public void consumeCancelOrder(String orderJson) {
        try {
            // 將接收到的 JSON 訂單轉換為 Order 對象
            Order order = objectMapper.readValue(orderJson, Order.class);

            // 取消訂單並更新 Redis 和 MySQL
            updateService.cancelOrder(order);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
