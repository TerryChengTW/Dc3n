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

    private long matchCount = 0;
    private Duration totalRedisFetchTime = Duration.ZERO;
    private Duration totalMatchExecutionTime = Duration.ZERO;
    private Duration totalRedisUpdateTime = Duration.ZERO;
    private Duration totalConditionCheckTime = Duration.ZERO;
    private Duration totalLoopTime = Duration.ZERO;

    @Autowired
    public NewOrderMatchingService(NewOrderbookService orderbookService) {
        this.orderbookService = orderbookService;
    }

    public void handleNewOrder(Order order) {
        orderbookService.saveOrderToRedis(order);
        // 確保只有當 unmatchedOrders 中該 symbol 不為 true 時，才啟動新的撮合
        synchronized (unmatchedOrders) {
            if (!Boolean.TRUE.equals(unmatchedOrders.get(order.getSymbol()))) {
                unmatchedOrders.put(order.getSymbol(), true);
                CompletableFuture.runAsync(() -> startContinuousMatching(order.getSymbol()));
            }
        }
    }

    public void startContinuousMatching(String symbol) {
        synchronized (symbol.intern()) {
            while (Boolean.TRUE.equals(unmatchedOrders.get(symbol))) {
                Instant loopStart = Instant.now();

                // 獲取訂單資料並計算時間
                Instant redisStart = Instant.now();
                Map<String, OrderSummary> topOrders = orderbookService.getTopBuyAndSellOrders(symbol);
                totalRedisFetchTime = totalRedisFetchTime.plus(Duration.between(redisStart, Instant.now()));

                OrderSummary highestBuyOrder = topOrders.get("highestBuyOrder");
                OrderSummary lowestSellOrder = topOrders.get("lowestSellOrder");

                // 判斷是否有可撮合的訂單並計算時間
                Instant conditionStart = Instant.now();
                boolean canMatch = (highestBuyOrder != null && lowestSellOrder != null &&
                        highestBuyOrder.getPrice().compareTo(lowestSellOrder.getPrice()) >= 0);
                totalConditionCheckTime = totalConditionCheckTime.plus(Duration.between(conditionStart, Instant.now()));

                // 如果沒有可撮合訂單，停止撮合並標記該 symbol 為未匹配狀態
                if (!canMatch) {
                    unmatchedOrders.put(symbol, false);
                    break;
                }

                // 如果有可撮合訂單，執行撮合並計算撮合時間
                matchOrders(highestBuyOrder, lowestSellOrder);
                matchCount++;

                // 計算整個循環的時間
                totalLoopTime = totalLoopTime.plus(Duration.between(loopStart, Instant.now()));

                // 每1000次撮合後，打印統計數據並重置
                if (matchCount % 1000 == 0) {
                    printAggregateStatistics();
                    resetStatistics();
                }
            }
        }
    }

    private void matchOrders(OrderSummary buyOrder, OrderSummary sellOrder) {
        Instant matchStart = Instant.now();

        // 計算撮合的數量
        BigDecimal matchQuantity = buyOrder.getUnfilledQuantity().min(sellOrder.getUnfilledQuantity());
        buyOrder.setUnfilledQuantity(buyOrder.getUnfilledQuantity().subtract(matchQuantity));
        sellOrder.setUnfilledQuantity(sellOrder.getUnfilledQuantity().subtract(matchQuantity));

        totalMatchExecutionTime = totalMatchExecutionTime.plus(Duration.between(matchStart, Instant.now()));

        // 合併更新買賣雙方訂單狀態
        Instant updateStart = Instant.now();
        orderbookService.updateOrdersStatusInRedis(buyOrder, sellOrder, matchQuantity); // 傳遞 matchQuantity
        totalRedisUpdateTime = totalRedisUpdateTime.plus(Duration.between(updateStart, Instant.now()));
    }

    private void printAggregateStatistics() {
        System.out.println("\nAggregate statistics for 1000 matches:");
        System.out.println("Total Redis fetch time: " + totalRedisFetchTime.toMillis() + " ms");
        System.out.println("Total match execution time: " + totalMatchExecutionTime.toMillis() + " ms");
        System.out.println("Total Redis update time: " + totalRedisUpdateTime.toMillis() + " ms");
        System.out.println("Total condition check time: " + totalConditionCheckTime.toMillis() + " ms");
        System.out.println("Total loop time: " + totalLoopTime.toMillis() + " ms");
        Duration accountedTime = totalRedisFetchTime.plus(totalMatchExecutionTime).plus(totalRedisUpdateTime).plus(totalConditionCheckTime);
        System.out.println("Total time accounted for: " + accountedTime.toMillis() + " ms");
        System.out.println("Unaccounted time: " + totalLoopTime.minus(accountedTime).toMillis() + " ms");
    }

    private void resetStatistics() {
        totalRedisFetchTime = Duration.ZERO;
        totalMatchExecutionTime = Duration.ZERO;
        totalRedisUpdateTime = Duration.ZERO;
        totalConditionCheckTime = Duration.ZERO;
        totalLoopTime = Duration.ZERO;
    }
}