package com.exchange.repository;

import com.exchange.model.Order;
import com.exchange.model.Trade;

public interface CustomTradeRepository {
    void saveAllOrdersAndTrade(Order buyOrder, Order sellOrder, Trade trade);
}
