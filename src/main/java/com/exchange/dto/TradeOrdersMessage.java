package com.exchange.dto;

import com.exchange.model.Order;
import com.exchange.model.Trade;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class  TradeOrdersMessage {
    // Getters and Setters
    private Order buyOrder;
    private Order sellOrder;
    private Trade trade;

}
