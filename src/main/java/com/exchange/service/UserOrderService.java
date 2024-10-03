package com.exchange.service;

import com.exchange.dto.OrderDTO;

import java.util.List;

public interface UserOrderService {
    List<OrderDTO> getUserOrders(String userId);
}