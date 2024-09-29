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
        // 將訂單保存到內存中的訂單簿
        orderbookService.saveOrderToOrderBook(order);

        // 執行撮合
        matchOrders(order.getSymbol());
    }

    // 撮合邏輯
    public void matchOrders(String symbol) {
        while (true) {
            Order[] bestOrders = orderbookService.getBestBuyAndSellOrders(symbol);
            Order bestBuyOrder = bestOrders[0];
            Order bestSellOrder = bestOrders[1];

            if (bestBuyOrder == null || bestSellOrder == null) {
                // 沒有可撮合的訂單
                break;
            }

            BigDecimal buyPrice = bestBuyOrder.getPrice();
            BigDecimal sellPrice = bestSellOrder.getPrice();

            if (buyPrice.compareTo(sellPrice) >= 0) {
                // 價格匹配，執行交易
                BigDecimal buyQuantity = bestBuyOrder.getUnfilledQuantity();
                BigDecimal sellQuantity = bestSellOrder.getUnfilledQuantity();
                // 計算撮合數量
                BigDecimal matchedQuantity = buyQuantity.min(sellQuantity);

                // 執行撮合
                orderbookService.executeTrade(bestBuyOrder, bestSellOrder, matchedQuantity);
            } else {
                // 價格不匹配，停止撮合
                break;
            }
        }
    }
}
