package com.exchange.service;

import com.exchange.dto.OrderHistoryDTO;
import com.exchange.model.Order;
import java.time.Instant;
import java.util.List;

public interface OrderHistoryService {
    List<OrderHistoryDTO> getOrderHistory(String userId,
                                          String symbol,
                                          Instant startTime,
                                          Instant endTime,
                                          Order.OrderType orderType,
                                          Order.Side side,
                                          Order.OrderStatus status);
}