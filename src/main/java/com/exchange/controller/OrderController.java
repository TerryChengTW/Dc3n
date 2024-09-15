package com.exchange.controller;

import com.exchange.dto.OrderRequest;
import com.exchange.model.Order;
import com.exchange.service.OrderService;
import com.exchange.utils.SnowflakeIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final SnowflakeIdGenerator idGenerator;

    @Autowired
    public OrderController(OrderService orderService, SnowflakeIdGenerator idGenerator) {
        this.orderService = orderService;
        this.idGenerator = idGenerator;
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submitOrder(@RequestBody OrderRequest orderRequest, HttpServletRequest request) {
        try {
            String userId = (String) request.getAttribute("userId");

            Order order = new Order();
            order.setId(String.valueOf(idGenerator.nextId()));
            order.setUserId(userId);
            order.setSymbol(orderRequest.getSymbol());
            order.setPrice(orderRequest.getPrice());
            order.setQuantity(orderRequest.getQuantity());
            order.setSide(orderRequest.getSide());
            order.setOrderType(orderRequest.getOrderType());
            order.setStatus(Order.OrderStatus.PENDING);
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            // 保存訂單並發送到 Kafka
            orderService.saveOrder(order);

            Map<String, String> response = new HashMap<>();
            response.put("message", "訂單提交成功");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            // 捕獲驗證失敗的異常並返回具體的錯誤訊息
            Map<String, String> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);  // 返回具體的錯誤訊息
        } catch (Exception e) {
            // 捕獲其他可能的異常
            Map<String, String> response = new HashMap<>();
            response.put("error", "訂單提交失敗，請稍後再試");
            return ResponseEntity.status(500).body(response);  // 返回一般錯誤訊息
        }
    }

    @PutMapping("/modify/{orderId}")
    public ResponseEntity<?> modifyOrder(@PathVariable String orderId, @RequestBody OrderRequest orderRequest) {
        Order order = orderService.getOrderById(orderId).orElse(null);
        if (order == null) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "訂單未找到");
            return ResponseEntity.badRequest().body(response);  // 返回 JSON
        }

        if (order.getStatus() != Order.OrderStatus.PENDING) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "訂單無法修改");
            return ResponseEntity.badRequest().body(response);  // 返回 JSON
        }

        // 更新訂單資料
        order.setPrice(orderRequest.getPrice());
        order.setQuantity(orderRequest.getQuantity());
        order.setUpdatedAt(LocalDateTime.now());

        // 更新訂單並發送到 Kafka
        orderService.updateOrder(order);

        Map<String, String> response = new HashMap<>();
        response.put("message", "訂單修改成功");

        return ResponseEntity.ok(response);  // 返回 JSON
    }
}
