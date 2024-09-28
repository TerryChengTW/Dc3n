package com.exchange.controller;

import com.exchange.dto.OrderRequest;
import com.exchange.model.Order;
import com.exchange.service.OrderService;
import com.exchange.utils.ApiResponse;
import com.exchange.utils.OrderProcessingTracker;
import com.exchange.utils.SnowflakeIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;

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

    // 提交新訂單
    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<?>> submitOrder(@RequestBody OrderRequest orderRequest, HttpServletRequest request) {
        String orderId = String.valueOf(idGenerator.nextId());
        OrderProcessingTracker.startTracking(orderId);
        try {
            String userId = (String) request.getAttribute("userId");

            // 確認是否有從 JWT 中提取到 userId
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>("JWT 無效或過期", "40301"));
            }

            Order order = new Order();
            order.setId(orderId);
            order.setUserId(userId);
            order.setSymbol(orderRequest.getSymbol());
            order.setPrice(orderRequest.getPrice());
            order.setQuantity(orderRequest.getQuantity());
            order.setUnfilledQuantity(orderRequest.getQuantity());
            order.setSide(orderRequest.getSide());
            order.setOrderType(orderRequest.getOrderType());
            order.setStatus(Order.OrderStatus.PENDING);
            order.setCreatedAt(Instant.now());
            order.setUpdatedAt(Instant.now());
            order.setModifiedAt(Instant.now());

            // 保存訂單並發送到 Kafka
            orderService.saveOrder(order);

            return ResponseEntity.ok(new ApiResponse<>("訂單提交成功", order));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(e.getMessage(), "40001"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse<>("訂單提交失敗，請稍後再試", "50001"));
        }
    }

    // 更新訂單
    @PutMapping("/modify/{orderId}")
    public ResponseEntity<ApiResponse<?>> modifyOrder(
            @PathVariable String orderId,
            @RequestBody OrderRequest orderRequest,
            HttpServletRequest request) {
        try {
            // 從 JWT 中提取 userId
            String userId = (String) request.getAttribute("userId");

            // 根據 orderId 查找訂單
            Order order = orderService.getOrderById(orderId).orElse(null);
            if (order == null) {
                return ResponseEntity.badRequest().body(new ApiResponse<>("訂單未找到", "40401"));
            }

            // 驗證訂單的擁有者是否與 JWT 中的 userId 一致
            if (!order.getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>("無權修改此訂單", "40302"));
            }

            // 更新訂單資料
            BigDecimal newQuantity = orderRequest.getQuantity();
            order.setPrice(orderRequest.getPrice());
            order.setQuantity(newQuantity);
            order.setUpdatedAt(Instant.now());
            order.setModifiedAt(Instant.now());

            // 更新訂單
            orderService.updateOrder(order, newQuantity);

            return ResponseEntity.ok(new ApiResponse<>("訂單修改成功", order));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(e.getMessage(), "40002"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse<>("訂單更新失敗，請稍後再試", "50002"));
        }
    }


    // 取消訂單
    @DeleteMapping("/cancel/{orderId}")
    public ResponseEntity<ApiResponse<?>> cancelOrder(
            @PathVariable String orderId,
            HttpServletRequest request) {
        try {
            String userId = (String) request.getAttribute("userId");

            Order order = orderService.getOrderById(orderId).orElse(null);
            if (order == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>("訂單未找到", "40401"));
            }

            if (!order.getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>("無權取消此訂單", "40302"));
            }

            if (order.getStatus() == Order.OrderStatus.COMPLETED || order.getStatus() == Order.OrderStatus.CANCELLED) {
                return ResponseEntity.badRequest().body(new ApiResponse<>("訂單已完成或已取消", "40003"));
            }

            orderService.cancelOrder(order);

            return ResponseEntity.ok(new ApiResponse<>("訂單已取消", order));

        } catch (Exception e) {
            // 打印異常訊息
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>("訂單取消失敗，請稍後再試", "50003"));
        }
    }

}
