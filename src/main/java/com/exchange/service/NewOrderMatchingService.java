package com.exchange.service;

import com.exchange.model.Order;
import com.exchange.model.Trade;
import com.exchange.utils.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class NewOrderMatchingService {

    private final NewOrderbookService orderbookService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    public NewOrderMatchingService(NewOrderbookService orderbookService, SnowflakeIdGenerator snowflakeIdGenerator) {
        this.orderbookService = orderbookService;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    public void handleNewOrder(Order order) {
        // 在嘗試將新訂單存入 Redis 之前，先進行撮合
        matchOrders(order);

        // 未完全匹配的訂單才存入 Redis
        if (order.getUnfilledQuantity().compareTo(BigDecimal.ZERO) > 0) {
            orderbookService.saveOrderToRedis(order);
        }
    }

    // 撮合邏輯
    public void matchOrders(Order newOrder) {
        // 循環，直到新訂單完全匹配或無法再匹配
        while (newOrder.getUnfilledQuantity().compareTo(BigDecimal.ZERO) > 0) {
            // 從 Redis 中獲取對手方的最佳訂單
            Order p1 = orderbookService.getBestOpponentOrder(newOrder);

            // 如果沒有對手方訂單，則跳出循環
            if (p1 == null) {
                System.out.println("No opponent order available for matching.");
                break;
            }

            // 檢查價格是否匹配
            boolean isPriceMatch = (newOrder.getSide() == Order.Side.BUY && newOrder.getPrice().compareTo(p1.getPrice()) >= 0) ||
                    (newOrder.getSide() == Order.Side.SELL && newOrder.getPrice().compareTo(p1.getPrice()) <= 0);

            if (isPriceMatch) {
                // 計算匹配的數量
                BigDecimal matchedQuantity = newOrder.getUnfilledQuantity().min(p1.getUnfilledQuantity());

                // 更新兩個訂單的數量
                newOrder.setFilledQuantity(newOrder.getFilledQuantity().add(matchedQuantity));
                newOrder.setUnfilledQuantity(newOrder.getUnfilledQuantity().subtract(matchedQuantity));

                p1.setFilledQuantity(p1.getFilledQuantity().add(matchedQuantity));
                p1.setUnfilledQuantity(p1.getUnfilledQuantity().subtract(matchedQuantity));

                // 如果 `p1` 被完全匹配，將其狀態設為 `COMPLETED`
                if (p1.getUnfilledQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    p1.setStatus(Order.OrderStatus.COMPLETED);
                    // 從 Redis ZSet 中刪除 `p1`
                    orderbookService.removeOrderFromRedis(p1);
                } else {
                    // 如果 `p1` 被部分匹配，更新其狀態為 `PARTIALLY_FILLED` 並更新在 Redis
                    p1.setStatus(Order.OrderStatus.PARTIALLY_FILLED);
                    orderbookService.updateOrderInRedis(p1);
                }

                // 如果 `newOrder` 被完全匹配，設為 `COMPLETED`
                if (newOrder.getUnfilledQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    newOrder.setStatus(Order.OrderStatus.COMPLETED);
                } else {
                    newOrder.setStatus(Order.OrderStatus.PARTIALLY_FILLED);
                }

                // 建立交易並持久化到 MySQL
                Trade trade = new Trade();
                trade.setId(String.valueOf(snowflakeIdGenerator.nextId()));
                trade.setBuyOrder(newOrder.getSide() == Order.Side.BUY ? newOrder : p1);
                trade.setSellOrder(newOrder.getSide() == Order.Side.SELL ? newOrder : p1);
                trade.setSymbol(newOrder.getSymbol());
                trade.setPrice(p1.getPrice());
                trade.setQuantity(matchedQuantity);
                trade.setTradeTime(Instant.now());
                orderbookService.saveTradeToDatabase(trade);

                System.out.println("Matched Trade: " + trade);
            } else {
                System.out.println("No price match for order: " + newOrder);
                break;
            }
        }
    }
}
