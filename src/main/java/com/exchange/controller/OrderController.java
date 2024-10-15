package com.exchange.controller;

import com.exchange.dto.OrderRequest;
import com.exchange.model.Order;
import com.exchange.producer.OrderBookDeltaProducer;
import com.exchange.repository.OrderRepository;
import com.exchange.service.OrderModifyService;
import com.exchange.service.OrderService;
import com.exchange.utils.ApiResponse;
import com.exchange.utils.SnowflakeIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final SnowflakeIdGenerator idGenerator;
    private final OrderModifyService orderModifyService;
    private final OrderRepository orderRepository;
    private final OrderBookDeltaProducer orderBookDeltaProducer;

    @Autowired
    public OrderController(OrderService orderService, SnowflakeIdGenerator idGenerator, OrderModifyService orderModifyService, OrderRepository orderRepository, OrderBookDeltaProducer orderBookDeltaProducer) {
        this.orderService = orderService;
        this.idGenerator = idGenerator;
        this.orderModifyService = orderModifyService;
        this.orderRepository = orderRepository;
        this.orderBookDeltaProducer = orderBookDeltaProducer;
    }

    // 提交新訂單
    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<?>> submitOrder(@RequestBody OrderRequest orderRequest, HttpServletRequest request) {
        String orderId = String.valueOf(idGenerator.nextId());
        try {
            String userId = (String) request.getAttribute("userId");

            // 確認是否有從 JWT 中提取到 userId
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>("JWT 無效或過期", 40301));
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
            return ResponseEntity.badRequest().body(new ApiResponse<>(e.getMessage(), 40001));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse<>("訂單提交失敗，請稍後再試", 50001));
        }
    }

    // 更新訂單
    @PutMapping("/modify/{orderId}")
    public ResponseEntity<ApiResponse<?>> modifyOrder(
            @PathVariable String orderId,
            @RequestBody OrderRequest orderRequest,
            HttpServletRequest request) {
        try {
            String userId = (String) request.getAttribute("userId");

            String symbol = orderRequest.getSymbol();
            Order.Side side = orderRequest.getSide();
            BigDecimal oldPrice = orderRequest.getOldPrice(); // 舊的價格
            BigDecimal newPrice = orderRequest.getPrice(); // 新的價格
            BigDecimal newQuantity = orderRequest.getQuantity(); // 新的數量
            long modifiedAt = orderRequest.getModifiedAt().toInstant().toEpochMilli(); // 假設前端傳遞 ZonedDateTime

            // 檢查並移除 Redis 中的舊訂單（使用舊價格查詢）
            ResponseEntity<ApiResponse<?>> response = orderModifyService.checkAndRemoveOrderFromRedis(symbol, side.name(), orderId, oldPrice, modifiedAt, userId);

            // 如果訂單未找到或驗證失敗，直接返回相應的響應
            if (response.getStatusCode() != HttpStatus.OK) {
                return response;
            }

            // 進行後續訂單更新邏輯，如發送到 Kafka

            return ResponseEntity.ok(new ApiResponse<>("訂單修改成功", orderId));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(e.getMessage(), 40002));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse<>("訂單更新失敗，請稍後再試", 50002));
        }
    }

    // 取消訂單
    @PutMapping("/cancel/{orderId}")
    public ResponseEntity<ApiResponse<?>> cancelOrder(
            @PathVariable String orderId,
            @RequestBody OrderRequest orderRequest, // 新增需要傳遞的資料
            HttpServletRequest request) {
        try {
            String userId = (String) request.getAttribute("userId");

            // 從前端請求中提取訂單信息
            String symbol = orderRequest.getSymbol();
            Order.Side side = orderRequest.getSide();
            BigDecimal price = orderRequest.getPrice(); // 訂單的價格
            long modifiedAt = orderRequest.getModifiedAt().toInstant().toEpochMilli(); // 修改時間

            // 查詢 Redis 中的訂單
            ResponseEntity<ApiResponse<?>> response = orderModifyService.checkAndRemoveOrderFromRedis(symbol, side.name(), orderId, price, modifiedAt, userId);

            // 如果訂單未找到或驗證失敗，直接返回相應的響應
            if (response.getStatusCode() != HttpStatus.OK) {
                return response;
            }

            // 取得要移除的訂單
            Order order = (Order) response.getBody().getData();

            // 將取消的訂單狀態寫入 MySQL
            order.setStatus(Order.OrderStatus.CANCELLED);
            order.setUpdatedAt(Instant.now());
            order.setModifiedAt(Instant.now());

            // 保存到數據庫
            orderRepository.save(order);

            orderBookDeltaProducer.sendDelta(
                    order.getSymbol(),
                    order.getSide().toString(),
                    order.getPrice().toString(),
                    "-" + order.getUnfilledQuantity().toString()
            );

            return ResponseEntity.ok(new ApiResponse<>("訂單取消成功，已更新到資料庫", order));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(e.getMessage(), "40004"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse<>("訂單取消失敗，請稍後再試", "50003"));
        }
    }
}
