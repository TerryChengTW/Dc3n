package com.exchange.controller;

import com.exchange.dto.OrderDTO;
import com.exchange.service.UserOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class UserOrderController {

    private final UserOrderService userOrderService;

    @Autowired
    public UserOrderController(UserOrderService userOrderService) {
        this.userOrderService = userOrderService;
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OrderDTO>> getUserOrders(@PathVariable String userId) {
        List<OrderDTO> orders = userOrderService.getUserOrders(userId);
        return ResponseEntity.ok(orders);
    }
}