package com.exchange.service;

import com.exchange.model.Order;
import com.exchange.model.OrderSummary;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Service
public class NewOrderMatchingService {

    private final NewOrderbookService orderbookService;
    // 使用 ConcurrentHashMap 來追蹤未匹配訂單（Symbol -> 是否需要撮合）
    private final ConcurrentHashMap<String, Boolean> unmatchedOrders = new ConcurrentHashMap<>();

    @Autowired
    public NewOrderMatchingService(NewOrderbookService orderbookService) {
        this.orderbookService = orderbookService;
    }

    // 處理新訂單的進入
    public void handleNewOrder(Order order) {
        System.out.println("Received new order: " + order);

        // 將新訂單保存到 Redis
        orderbookService.saveOrderToRedis(order);
        System.out.println("Order saved to Redis: " + order);

        // 更新未匹配訂單的狀態，並通知撮合引擎
        unmatchedOrders.put(order.getSymbol(), true);
        System.out.println("Unmatched order status updated for symbol: " + order.getSymbol());

        // 異步觸發撮合邏輯
        CompletableFuture.runAsync(() -> startContinuousMatching(order.getSymbol()));
    }

    // 持續撮合買賣訂單
    public void startContinuousMatching(String symbol) {
        synchronized (symbol.intern()) {
            // 只在有未匹配的訂單時進行撮合
            while (Boolean.TRUE.equals(unmatchedOrders.get(symbol))) {
                System.out.println("Checking for orders to match for symbol: " + symbol);

                // 合併查詢最高買價和最低賣價訂單
                Map<String, OrderSummary> topOrders = orderbookService.getTopBuyAndSellOrders(symbol);
                OrderSummary highestBuyOrder = topOrders.get("highestBuyOrder");
                OrderSummary lowestSellOrder = topOrders.get("lowestSellOrder");

                // 調試輸出最優買賣訂單的細節
                System.out.println("Highest Buy Order: " + highestBuyOrder);
                System.out.println("Lowest Sell Order: " + lowestSellOrder);

                // 如果無法匹配訂單，退出輪詢
                if (highestBuyOrder == null || lowestSellOrder == null || highestBuyOrder.getPrice().compareTo(lowestSellOrder.getPrice()) < 0) {
                    System.out.println("No matching possible for symbol: " + symbol);
                    unmatchedOrders.put(symbol, false);
                    break;
                }

                // 調試輸出正在進行匹配的訂單價格與數量
                System.out.println("Matching Orders - Buy ID: " + highestBuyOrder.getOrderId() + " Price: " + highestBuyOrder.getPrice() + " Quantity: " + highestBuyOrder.getUnfilledQuantity());
                System.out.println("Matching Orders - Sell ID: " + lowestSellOrder.getOrderId() + " Price: " + lowestSellOrder.getPrice() + " Quantity: " + lowestSellOrder.getUnfilledQuantity());

                // 執行撮合邏輯
                matchOrders(highestBuyOrder, lowestSellOrder);
            }
        }
    }

    // 撮合買賣雙方訂單
    private void matchOrders(OrderSummary buyOrder, OrderSummary sellOrder) {
        // 撮合數量：取買賣雙方的最小未成交數量
        BigDecimal matchQuantity = buyOrder.getUnfilledQuantity().min(sellOrder.getUnfilledQuantity());

        // 調試輸出撮合數量
        System.out.println("Matching Quantity: " + matchQuantity);

        // 更新未成交數量
        buyOrder.setUnfilledQuantity(buyOrder.getUnfilledQuantity().subtract(matchQuantity));
        sellOrder.setUnfilledQuantity(sellOrder.getUnfilledQuantity().subtract(matchQuantity));

        // 調試輸出匹配後的未成交數量
        System.out.println("Updated Buy Order - ID: " + buyOrder.getOrderId() + ", Symbol: " + buyOrder.getSymbol() + ", Unfilled Quantity: " + buyOrder.getUnfilledQuantity());
        System.out.println("Updated Sell Order - ID: " + sellOrder.getOrderId() + ", Symbol: " + sellOrder.getSymbol() + ", Unfilled Quantity: " + sellOrder.getUnfilledQuantity());

        // 完全成交時從 Redis 中移除，並持久化到 MySQL
        if (buyOrder.getUnfilledQuantity().compareTo(BigDecimal.ZERO) == 0) {
            Order completedBuyOrder = orderbookService.removeOrderAndPersistToDatabase(buyOrder);
            System.out.println("Buy Order fully matched and removed from Redis - ID: " + completedBuyOrder.getId());
            System.out.println("Completed Buy Order: " + completedBuyOrder);
        } else {
            // 使用新方法更新部分匹配訂單並持久化
            Order partiallyFilledBuyOrder = orderbookService.updateOrderInZSetAndPersistToDatabase(buyOrder);
            System.out.println("Buy Order partially matched and persisted - ID: " + partiallyFilledBuyOrder.getId());
            System.out.println("Partially Filled Buy Order: " + partiallyFilledBuyOrder);
        }

        if (sellOrder.getUnfilledQuantity().compareTo(BigDecimal.ZERO) == 0) {
            Order completedSellOrder = orderbookService.removeOrderAndPersistToDatabase(sellOrder);
            System.out.println("Sell Order fully matched and removed from Redis - ID: " + completedSellOrder.getId());
            System.out.println("Completed Sell Order: " + completedSellOrder);
        } else {
            // 使用新方法更新部分匹配訂單並持久化
            Order partiallyFilledSellOrder = orderbookService.updateOrderInZSetAndPersistToDatabase(sellOrder);
            System.out.println("Sell Order partially matched and persisted - ID: " + partiallyFilledSellOrder.getId());
            System.out.println("Partially Filled Sell Order: " + partiallyFilledSellOrder);
        }

        // 調試輸出匹配完成的結果
        System.out.println("Matched Order - Buy Order ID: " + buyOrder.getOrderId() + ", Sell Order ID: " + sellOrder.getOrderId() + ", Matched Quantity: " + matchQuantity);

        // TODO: 將撮合結果持久化到 MySQL 或其他存儲
    }

}
