package com.exchange.service;

import com.exchange.model.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class NewOrderMatchingService {

    private final NewOrderbookService orderbookService;
    private int orderCount = 0; // 用於計算訂單數量
    private long startTime = System.currentTimeMillis(); // 記錄開始時間

    @Autowired
    public NewOrderMatchingService(NewOrderbookService orderbookService) {
        this.orderbookService = orderbookService;
    }

    public void handleNewOrder(Order order) {
        orderbookService.saveOrderToRedis(order);
        matchOrders(order.getSymbol());

        // 計數訂單
        orderCount++;
        if (orderCount % 1000 == 0) {
            // 計算耗時
            long currentTime = System.currentTimeMillis();
            long duration = currentTime - startTime;
            System.out.println("Processed 1000 orders in " + duration + " ms.");

            // 重置計時器和計數
            startTime = currentTime;
            orderCount = 0;
        }
    }

    // 撮合邏輯
    public void matchOrders(String symbol) {
        // 合併查詢最佳買單和賣單
        Order[] bestOrders = orderbookService.getBestBuyAndSellOrders(symbol);
        Order bestBuyOrder = bestOrders[0];
        Order bestSellOrder = bestOrders[1];

        if (bestBuyOrder == null || bestSellOrder == null) {
            // 沒有可撮合的訂單
            return;
        }

        BigDecimal buyPrice = bestBuyOrder.getPrice();
        BigDecimal sellPrice = bestSellOrder.getPrice();

        if (buyPrice.compareTo(sellPrice) >= 0) {
            // 價格可匹配，開始撮合
            BigDecimal buyQuantity = bestBuyOrder.getUnfilledQuantity();
            BigDecimal sellQuantity = bestSellOrder.getUnfilledQuantity();

            // 計算撮合數量
            BigDecimal matchedQuantity = buyQuantity.min(sellQuantity);

            // 執行撮合
            orderbookService.executeTrade(bestBuyOrder, bestSellOrder, matchedQuantity);
        }
    }
}
