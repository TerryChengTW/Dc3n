package com.exchange.service;

import com.exchange.model.Order;
import com.exchange.model.OrderSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

import java.time.Instant;
import java.time.Duration;

@Service
public class NewOrderMatchingService {

    private final NewOrderbookService orderbookService;
    private final ConcurrentHashMap<String, Boolean> unmatchedOrders = new ConcurrentHashMap<>();

    // 匹配次數計數器
    private int matchCount = 0;
    // 開始計時的時間
    private Instant startTime;

    // 各步驟時間累加器
    private long redisFetchTime = 0;
    private long matchExecutionTime = 0;
    private long redisUpdateTime = 0;
    private long dbPersistTime = 0;

    @Autowired
    public NewOrderMatchingService(NewOrderbookService orderbookService) {
        this.orderbookService = orderbookService;
        // 初始化計時器
        this.startTime = Instant.now();
    }


    // 處理新訂單的進入
    public void handleNewOrder(Order order) {
        // 將新訂單保存到 Redis
        orderbookService.saveOrderToRedis(order);
        // 更新未匹配訂單的狀態，並通知撮合引擎
        unmatchedOrders.put(order.getSymbol(), true);
        // 異步觸發撮合邏輯
        CompletableFuture.runAsync(() -> startContinuousMatching(order.getSymbol()));
    }

    // 持續撮合買賣訂單
    public void startContinuousMatching(String symbol) {
        synchronized (symbol.intern()) {
            while (Boolean.TRUE.equals(unmatchedOrders.get(symbol))) {
                // 記錄 Redis 查詢訂單的時間
                Instant redisStart = Instant.now();
                Map<String, OrderSummary> topOrders = orderbookService.getTopBuyAndSellOrders(symbol);
                OrderSummary highestBuyOrder = topOrders.get("highestBuyOrder");
                OrderSummary lowestSellOrder = topOrders.get("lowestSellOrder");
                redisFetchTime += Duration.between(redisStart, Instant.now()).toMillis();

                if (highestBuyOrder == null || lowestSellOrder == null || highestBuyOrder.getPrice().compareTo(lowestSellOrder.getPrice()) < 0) {
                    unmatchedOrders.put(symbol, false);
                    break;
                }

                // 執行撮合邏輯，並計算撮合的執行時間
                Instant matchStart = Instant.now();
                matchOrders(highestBuyOrder, lowestSellOrder);
                matchExecutionTime += Duration.between(matchStart, Instant.now()).toMillis();
            }
        }
    }

    // 撮合買賣雙方訂單
    private void matchOrders(OrderSummary buyOrder, OrderSummary sellOrder) {
        // 撮合數量：取買賣雙方的最小未成交數量
        BigDecimal matchQuantity = buyOrder.getUnfilledQuantity().min(sellOrder.getUnfilledQuantity());

        // 更新未成交數量
        buyOrder.setUnfilledQuantity(buyOrder.getUnfilledQuantity().subtract(matchQuantity));
        sellOrder.setUnfilledQuantity(sellOrder.getUnfilledQuantity().subtract(matchQuantity));

        // 完全成交時從 Redis 中移除，並持久化到 MySQL
        if (buyOrder.getUnfilledQuantity().compareTo(BigDecimal.ZERO) == 0) {
            Instant redisUpdateStart = Instant.now();
            orderbookService.removeOrderAndPersistToDatabase(buyOrder);
            redisUpdateTime += Duration.between(redisUpdateStart, Instant.now()).toMillis();
        } else {
            Instant redisUpdateStart = Instant.now();
            orderbookService.updateOrderInZSetAndPersistToDatabase(buyOrder);
            redisUpdateTime += Duration.between(redisUpdateStart, Instant.now()).toMillis();
        }

        if (sellOrder.getUnfilledQuantity().compareTo(BigDecimal.ZERO) == 0) {
            Instant redisUpdateStart = Instant.now();
            orderbookService.removeOrderAndPersistToDatabase(sellOrder);
            redisUpdateTime += Duration.between(redisUpdateStart, Instant.now()).toMillis();
        } else {
            Instant redisUpdateStart = Instant.now();
            orderbookService.updateOrderInZSetAndPersistToDatabase(sellOrder);
            redisUpdateTime += Duration.between(redisUpdateStart, Instant.now()).toMillis();
        }

        // 增加匹配次數
        matchCount++;

        // 每 1000 次匹配計算用時
        if (matchCount % 1000 == 0) {
            Instant endTime = Instant.now();
            Duration duration = Duration.between(startTime, endTime);
            System.out.println("Time taken for 1000 matches: " + duration.toMillis() + " ms");

            // 打印各步驟平均耗時
            System.out.println("Average time per step for 1000 matches:");
            System.out.println("Redis fetch: " + (redisFetchTime / 1000.0) + " ms");
            System.out.println("Matching execution: " + (matchExecutionTime / 1000.0) + " ms");
            System.out.println("Redis update: " + (redisUpdateTime / 1000.0) + " ms");

            // 重置計時器和累加器
            startTime = Instant.now();
            redisFetchTime = 0;
            matchExecutionTime = 0;
            redisUpdateTime = 0;
            dbPersistTime = 0;
        }
    }
}
