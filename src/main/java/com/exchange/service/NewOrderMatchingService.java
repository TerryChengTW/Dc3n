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

    // 細部時間統計
    private long totalSaveOrderDuration = 0;
    private long totalMatchOrderDuration = 0;
    private long totalGetBestOrderDuration = 0;
    private long totalPriceComparisonDuration = 0;
    private long totalQuantityCalculationDuration = 0;
    private long totalExecuteTradeDuration = 0;

    @Autowired
    public NewOrderMatchingService(NewOrderbookService orderbookService) {
        this.orderbookService = orderbookService;
    }

    public void handleNewOrder(Order order) {
        long start = System.currentTimeMillis(); // 記錄整個方法開始時間

        // 記錄 saveOrderToRedis 開始時間
        long saveStartTime = System.currentTimeMillis();
        orderbookService.saveOrderToRedis(order);
        // 計算 saveOrderToRedis 耗時
        totalSaveOrderDuration += System.currentTimeMillis() - saveStartTime;

        // 記錄 matchOrders 開始時間
        long matchStartTime = System.currentTimeMillis();
        matchOrders(order.getSymbol());
        // 計算 matchOrders 耗時
        totalMatchOrderDuration += System.currentTimeMillis() - matchStartTime;

        // 計數訂單
        orderCount++;
        if (orderCount % 1000 == 0) {
            // 計算總耗時
            long currentTime = System.currentTimeMillis();
            long duration = currentTime - startTime;

            // 計算額外的開銷時間
            long otherDuration = duration - (totalSaveOrderDuration + totalMatchOrderDuration);

            // 打印細部時間統計
            System.out.println("Processed 1000 orders in " + duration + " ms.");
            System.out.println(" - Total saveOrderToRedis duration: " + totalSaveOrderDuration + " ms.");
            System.out.println(" - Total matchOrders duration: " + totalMatchOrderDuration + " ms.");
            System.out.println(" - Other operations duration: " + otherDuration + " ms.");
            System.out.println("   - Total getBestOrder duration: " + totalGetBestOrderDuration + " ms.");
            System.out.println("   - Total price comparison duration: " + totalPriceComparisonDuration + " ms.");
            System.out.println("   - Total quantity calculation duration: " + totalQuantityCalculationDuration + " ms.");
            System.out.println("   - Total executeTrade duration: " + totalExecuteTradeDuration + " ms.");

            // 重置計時器和計數
            startTime = currentTime;
            orderCount = 0;
            totalSaveOrderDuration = 0;
            totalMatchOrderDuration = 0;
            totalGetBestOrderDuration = 0;
            totalPriceComparisonDuration = 0;
            totalQuantityCalculationDuration = 0;
            totalExecuteTradeDuration = 0;
        }
    }


    // 撮合邏輯
    public void matchOrders(String symbol) {
        // 記錄獲取最佳訂單的開始時間
        long getBestOrderStart = System.currentTimeMillis();
        Order[] bestOrders = orderbookService.getBestBuyAndSellOrders(symbol);
        Order bestBuyOrder = bestOrders[0];
        Order bestSellOrder = bestOrders[1];
        totalGetBestOrderDuration += System.currentTimeMillis() - getBestOrderStart;

        if (bestBuyOrder == null || bestSellOrder == null) {
            // 沒有可撮合的訂單
            return;
        }

        // 記錄價格比較開始時間
        long priceComparisonStart = System.currentTimeMillis();
        BigDecimal buyPrice = bestBuyOrder.getPrice();
        BigDecimal sellPrice = bestSellOrder.getPrice();
        boolean isPriceMatch = buyPrice.compareTo(sellPrice) >= 0;
        totalPriceComparisonDuration += System.currentTimeMillis() - priceComparisonStart;

        if (isPriceMatch) {
            // 記錄數量計算開始時間
            long quantityCalculationStart = System.currentTimeMillis();
            BigDecimal buyQuantity = bestBuyOrder.getUnfilledQuantity();
            BigDecimal sellQuantity = bestSellOrder.getUnfilledQuantity();
            // 計算撮合數量
            BigDecimal matchedQuantity = buyQuantity.min(sellQuantity);
            totalQuantityCalculationDuration += System.currentTimeMillis() - quantityCalculationStart;

            // 記錄執行撮合開始時間
            long executeTradeStart = System.currentTimeMillis();
            // 執行撮合
            orderbookService.executeTrade(bestBuyOrder, bestSellOrder, matchedQuantity);
            totalExecuteTradeDuration += System.currentTimeMillis() - executeTradeStart;
        }
    }
}
