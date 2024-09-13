package com.exchange.controller;

import com.exchange.model.Order;
import com.exchange.model.User;
import com.exchange.producer.OrderProducer;
import com.exchange.service.OrderMatchingService;
import com.exchange.service.UserService;
import com.exchange.utils.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
public class TestController {

    @Autowired
    private OrderProducer orderProducer;

    @Autowired
    private OrderMatchingService orderMatchingService;

    @Autowired
    private UserService userService;

    private final SnowflakeIdGenerator idGenerator = new SnowflakeIdGenerator(1L, 1L); // 初始化 SnowflakeIdGenerator

    @GetMapping("/testKafkaTPS")
    public String testKafkaTPS(@RequestParam String userId) {
        long start = System.currentTimeMillis();
        int orderCount = 10000;

        Optional<User> optionalUser = userService.getUserById(userId);
        if (optionalUser.isEmpty()) {
            return "User not found!";
        }
        User user = optionalUser.get();

        for (int i = 0; i < orderCount; i++) {
            Order order = new Order(
                    Long.toString(idGenerator.nextId()),
                    user,
                    "BTC/USDT",
                    BigDecimal.valueOf(25000 + i),
                    BigDecimal.valueOf(1 + (i * 0.01)),
                    Order.Side.BUY,
                    Order.OrderType.LIMIT,
                    Order.OrderStatus.PENDING,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    null,
                    null
            );
            orderProducer.sendOrder(order);
        }

        long end = System.currentTimeMillis();
        long duration = end - start;
        return "Processed " + orderCount + " orders in Kafka in " + duration + " ms.";
    }

    @GetMapping("/testRedisTPS")
    public String testRedisTPS(@RequestParam String userId) {
        long start = System.currentTimeMillis();
        int orderCount = 10000;
        List<Order> orders = new ArrayList<>();

        Optional<User> optionalUser = userService.getUserById(userId);
        if (optionalUser.isEmpty()) {
            return "User not found!";
        }
        User user = optionalUser.get();

        for (int i = 0; i < orderCount; i++) {
            Order order = new Order(
                    Long.toString(idGenerator.nextId()), // 使用 Snowflake 算法生成的 ID
                    user,
                    "BTC/USDT",
                    BigDecimal.valueOf(25000 + i),
                    BigDecimal.valueOf(1 + (i * 0.01)),
                    Order.Side.BUY,
                    Order.OrderType.LIMIT,
                    Order.OrderStatus.PENDING,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    null,
                    null
            );
            orders.add(order);
        }

        orderMatchingService.processOrders(orders);

        long end = System.currentTimeMillis();
        long duration = end - start;
        return "Processed " + orderCount + " orders in Redis in " + duration + " ms.";
    }

    @GetMapping("/testMySQLTPS")
    public String testMySQLTPS(@RequestParam String userId) {
        long start = System.currentTimeMillis();
        int orderCount = 10000;
        List<Order> orders = new ArrayList<>();

        Optional<User> optionalUser = userService.getUserById(userId);
        if (optionalUser.isEmpty()) {
            return "User not found!";
        }
        User user = optionalUser.get();

        for (int i = 0; i < orderCount; i++) {
            Order order = new Order(
                    Long.toString(idGenerator.nextId()), // 使用 Snowflake 算法生成的 ID
                    user,
                    "BTC/USDT",
                    BigDecimal.valueOf(25000 + i),
                    BigDecimal.valueOf(1 + (i * 0.01)),
                    Order.Side.BUY,
                    Order.OrderType.LIMIT,
                    Order.OrderStatus.PENDING,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    null,
                    null
            );
            orders.add(order);
        }

        orderMatchingService.saveOrdersToDatabase(orders);

        long end = System.currentTimeMillis();
        long duration = end - start;
        return "Processed " + orderCount + " orders in MySQL in " + duration + " ms.";
    }
}
