package com.exchange.service;

import com.exchange.model.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class NewOrderMatchingService {

    private final NewOrderbookService orderbookService;

    @Autowired
    public NewOrderMatchingService(NewOrderbookService orderbookService) {
        this.orderbookService = orderbookService;
    }

    public void handleNewOrder(Order order) {

        orderbookService.saveOrderToRedis(order);

        matchOrders(order.getSymbol());
    }


    // 撮合邏輯
    public void matchOrders(String symbol) {
        Order[] bestOrders = orderbookService.getBestBuyAndSellOrders(symbol);
        Order bestBuyOrder = bestOrders[0];
        Order bestSellOrder = bestOrders[1];

        if (bestBuyOrder == null || bestSellOrder == null) {
            // 沒有可撮合的訂單
            return;
        }

        BigDecimal buyPrice = bestBuyOrder.getPrice();
        BigDecimal sellPrice = bestSellOrder.getPrice();
        boolean isPriceMatch = buyPrice.compareTo(sellPrice) >= 0;

        if (isPriceMatch) {
            BigDecimal buyQuantity = bestBuyOrder.getUnfilledQuantity();
            BigDecimal sellQuantity = bestSellOrder.getUnfilledQuantity();
            // 計算撮合數量
            BigDecimal matchedQuantity = buyQuantity.min(sellQuantity);

            // 執行撮合
            orderbookService.executeTrade(bestBuyOrder, bestSellOrder, matchedQuantity);
        }
    }
}
