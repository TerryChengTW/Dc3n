package com.exchange.service;

import com.exchange.model.Order;
import jakarta.annotation.PostConstruct;
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

    public void processMarketOrder(Order order) {
        // 撮合邏輯：查詢 ZSet，執行市場單撮合，更新訂單狀態...
    }

    @PostConstruct
    public void init() {
        // 使用單獨的線程來運行撮合邏輯，防止阻塞 Spring 應用程序
        new Thread(() -> startContinuousMatching("BTCUSDT")).start();
    }

    // 開始持續撮合買賣一檔訂單
    public void startContinuousMatching(String symbol) {
        while (true) {
            // 不停檢查最優買賣訂單
            Order highestBuyOrder = orderbookService.getHighestBuyOrder(symbol);
            Order lowestSellOrder = orderbookService.getLowestSellOrder(symbol);

            // 檢查訂單是否可以匹配
            if (highestBuyOrder != null && lowestSellOrder != null && highestBuyOrder.getPrice().compareTo(lowestSellOrder.getPrice()) >= 0) {
                // 進行撮合
                matchOrders(highestBuyOrder, lowestSellOrder);

                // 如果訂單完全成交，從 Redis 中移除
                if (highestBuyOrder.getUnfilledQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    orderbookService.removeOrderFromRedis(highestBuyOrder);
                }
                if (lowestSellOrder.getUnfilledQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    orderbookService.removeOrderFromRedis(lowestSellOrder);
                }
            }

            // 沒有可以匹配的訂單也不等待，立即進入下一次輪詢
        }
    }


    // 撮合買賣雙方訂單
    private void matchOrders(Order buyOrder, Order sellOrder) {
        // 計算可成交數量：取未成交數量的最小值
        BigDecimal matchQuantity = buyOrder.getUnfilledQuantity().min(sellOrder.getUnfilledQuantity());

        // 更新訂單的已成交數量
        buyOrder.setFilledQuantity(buyOrder.getFilledQuantity().add(matchQuantity));
        sellOrder.setFilledQuantity(sellOrder.getFilledQuantity().add(matchQuantity));

        // 更新訂單的未成交數量
        buyOrder.setUnfilledQuantity(buyOrder.getQuantity().subtract(buyOrder.getFilledQuantity()));
        sellOrder.setUnfilledQuantity(sellOrder.getQuantity().subtract(sellOrder.getFilledQuantity()));

        // 更新 Redis 中的訂單狀態
        orderbookService.updateOrderInRedis(buyOrder);
        orderbookService.updateOrderInRedis(sellOrder);

        // 打印撮合結果（可選）
        System.out.println("Matched Order - Buy Order ID: " + buyOrder.getId() + ", Sell Order ID: " + sellOrder.getId() + ", Quantity: " + matchQuantity);
    }
}
