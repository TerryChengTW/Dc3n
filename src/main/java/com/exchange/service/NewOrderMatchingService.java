package com.exchange.service;

import com.exchange.model.Order;
import com.exchange.model.OrderSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Service
public class NewOrderMatchingService {

    private final NewOrderbookService orderbookService;
    private final ConcurrentHashMap<String, Boolean> unmatchedOrders = new ConcurrentHashMap<>();

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

                // 獲取訂單資料
                Map<String, OrderSummary> topOrders = orderbookService.getTopBuyAndSellOrders(symbol);

                OrderSummary highestBuyOrder = topOrders.get("highestBuyOrder");
                OrderSummary lowestSellOrder = topOrders.get("lowestSellOrder");

                // 判斷是否有可撮合的訂單
                boolean canMatch = (highestBuyOrder != null && lowestSellOrder != null &&
                        highestBuyOrder.getPrice().compareTo(lowestSellOrder.getPrice()) >= 0);

                // 如果沒有可撮合訂單，停止撮合並標記該 symbol 為未匹配狀態
                if (!canMatch) {
                    unmatchedOrders.put(symbol, false);
                    break;
                }

                // 如果有可撮合訂單，執行撮合
                matchOrders(highestBuyOrder, lowestSellOrder);
            }
        }
    }

    private void matchOrders(OrderSummary buyOrder, OrderSummary sellOrder) {
        // 計算撮合的數量
        BigDecimal matchQuantity = buyOrder.getUnfilledQuantity().min(sellOrder.getUnfilledQuantity());
        buyOrder.setUnfilledQuantity(buyOrder.getUnfilledQuantity().subtract(matchQuantity));
        sellOrder.setUnfilledQuantity(sellOrder.getUnfilledQuantity().subtract(matchQuantity));

        // 合併更新買賣雙方訂單狀態
        orderbookService.updateOrdersStatusInRedis(buyOrder, sellOrder, matchQuantity);
    }
}
