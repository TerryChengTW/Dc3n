package com.exchange.service;

import com.exchange.model.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NewOrderMatchingService {

    private final NewOrderbookService orderbookService;
    @Autowired
    public NewOrderMatchingService(NewOrderbookService orderbookService) {
        this.orderbookService = orderbookService;
    }

    public void handleNewOrder(Order order) {
        orderbookService.saveOrderToRedis(order);
    }

}