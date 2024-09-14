package com.exchange.controller;

import com.exchange.dto.OrderRequest;
import com.exchange.model.Order;
import com.exchange.service.OrderService;
import com.exchange.utils.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

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
    public ResponseEntity<?> submitOrder(@RequestBody OrderRequest orderRequest) {
        // 檢查 userId 是否有效
        if (orderRequest.getUserId() == null || orderRequest.getUserId().isEmpty()) {
            return ResponseEntity.badRequest().body("無效的用戶ID");
        }

        // 創建新訂單
        Order order = new Order();
        order.setId(String.valueOf(idGenerator.nextId()));
        order.setUserId(orderRequest.getUserId());  // 設置 userId
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

        return ResponseEntity.ok("訂單提交成功");
    }

    @PutMapping("/modify/{orderId}")
    public ResponseEntity<?> modifyOrder(@PathVariable String orderId, @RequestBody OrderRequest orderRequest) {
        // 獲取現有訂單
        Order order = orderService.getOrderById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.badRequest().body("訂單未找到");
        }

        // 檢查訂單狀態
        if (order.getStatus() != Order.OrderStatus.PENDING) {
            return ResponseEntity.badRequest().body("訂單無法修改");
        }

        // 更新訂單資料
        order.setPrice(orderRequest.getPrice());
        order.setQuantity(orderRequest.getQuantity());
        order.setUpdatedAt(LocalDateTime.now());

        // 更新訂單並發送到 Kafka
        orderService.updateOrder(order);

        return ResponseEntity.ok("訂單修改成功");
    }
}
