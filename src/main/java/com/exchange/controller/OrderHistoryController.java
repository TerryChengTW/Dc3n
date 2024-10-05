package com.exchange.controller;

import com.exchange.dto.OrderHistoryDTO;
import com.exchange.model.Order;
import com.exchange.service.OrderHistoryService;
import com.exchange.service.OrderHistoryServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderHistoryController {

    private final OrderHistoryService orderHistoryService;

    @Autowired
    public OrderHistoryController(OrderHistoryService orderHistoryService) {
        this.orderHistoryService = orderHistoryService;
    }

    @GetMapping("/history")
    public ResponseEntity<List<OrderHistoryDTO>> getOrderHistory(
            HttpServletRequest request,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false, defaultValue = "1d") String timeRange,
            @RequestParam(required = false) Order.OrderType orderType,
            @RequestParam(required = false) Order.Side side,
            @RequestParam(required = false) Order.OrderStatus status
    ) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED).build();
        }

        Instant endTime = Instant.now();
        Instant startTime = getStartTime(timeRange, endTime);

        List<OrderHistoryDTO> orderHistory = orderHistoryService.getOrderHistory(userId, symbol, startTime, endTime, orderType, side, status);
        return ResponseEntity.ok(orderHistory);
    }

    private Instant getStartTime(String timeRange, Instant endTime) {
        switch (timeRange) {
            case "7":
                return endTime.minus(7, ChronoUnit.DAYS);
            case "3":
                return endTime.minus(3, ChronoUnit.DAYS);
            default:
                return endTime.minus(1, ChronoUnit.DAYS);
        }
    }
}