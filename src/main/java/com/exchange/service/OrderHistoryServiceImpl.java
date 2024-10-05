package com.exchange.service;

import com.exchange.dto.OrderHistoryDTO;
import com.exchange.dto.SimpleTradeInfo;
import com.exchange.model.Order;
import com.exchange.repository.OrderRepository;
import com.exchange.repository.TradeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderHistoryServiceImpl implements OrderHistoryService {

    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;

    @Autowired
    public OrderHistoryServiceImpl(OrderRepository orderRepository, TradeRepository tradeRepository) {
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
    }

    @Override
    public List<OrderHistoryDTO> getOrderHistory(String userId,
                                                 String symbol,
                                                 Instant startTime,
                                                 Instant endTime,
                                                 Order.OrderType orderType,
                                                 Order.Side side,
                                                 Order.OrderStatus status) {

        List<Order> orders = orderRepository.findOrderHistory(userId, symbol, startTime, endTime, orderType, side, status);

        return orders.stream()
                .map(order -> {
                    List<SimpleTradeInfo> trades = tradeRepository.findSimpleTradeInfoByOrderId(order.getId());
                    return new OrderHistoryDTO(order, trades);
                })
                .collect(Collectors.toList());
    }
}