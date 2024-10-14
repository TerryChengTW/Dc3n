package com.exchange.model;

import com.exchange.model.Order;
import lombok.Getter;

@Getter
public class OrderRecord {
    private final Order order;
    private final long receivedTime;

    public OrderRecord(Order order, long receivedTime) {
        this.order = order;
        this.receivedTime = receivedTime;
    }

}
