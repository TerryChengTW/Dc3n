package com.exchange.repository;

import com.exchange.model.Order;
import com.exchange.model.Trade;

import java.util.List;

public interface CustomTradeRepository {
    void saveAllOrdersAndTrades(List<Order> buyOrders, List<Order> sellOrders, List<Trade> trades);
}
