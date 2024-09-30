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
        // 在嘗試將新訂單存入 Redis 之前，先進行撮合
        matchOrders(order);

        // 未完全匹配的訂單才存入 Redis
        orderbookService.saveOrderToRedis(order);
    }

    // 撮合邏輯
    public void matchOrders(Order newOrder) {
        // 根據新訂單方向獲取對手方的最佳訂單
        Order opponentOrder = orderbookService.getBestOpponentOrder(newOrder);

        // 如果沒有對手方訂單，則不進行匹配
        if (opponentOrder == null) {
            System.out.println("No opponent order available for matching.");
            return;
        }

        // 檢查價格是否匹配
        boolean isPriceMatch = (newOrder.getSide() == Order.Side.BUY && newOrder.getPrice().compareTo(opponentOrder.getPrice()) >= 0) ||
                (newOrder.getSide() == Order.Side.SELL && newOrder.getPrice().compareTo(opponentOrder.getPrice()) <= 0);

        if (isPriceMatch) {
            // 打印匹配結果
            System.out.println("Matched Order: " + newOrder);
            System.out.println("Opponent Order: " + opponentOrder);
        } else {
            System.out.println("No price match for order: " + newOrder);
        }
    }
}
