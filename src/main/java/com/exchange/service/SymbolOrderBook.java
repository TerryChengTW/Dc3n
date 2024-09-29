package com.exchange.service;

import com.exchange.model.Order;

import java.math.BigDecimal;
import java.util.*;

public class SymbolOrderBook {
    private final NavigableMap<BigDecimal, Queue<Order>> buyOrders;
    private final NavigableMap<BigDecimal, Queue<Order>> sellOrders;

    public SymbolOrderBook() {
        this.buyOrders = new TreeMap<>(Collections.reverseOrder()); // 最高買價優先
        this.sellOrders = new TreeMap<>(); // 最低賣價優先
    }

    // 添加訂單到訂單簿
    public void addOrder(Order order) {
        NavigableMap<BigDecimal, Queue<Order>> orderBook = order.getSide() == Order.Side.BUY ? buyOrders : sellOrders;
        BigDecimal price = order.getPrice();
        Queue<Order> ordersAtPrice = orderBook.computeIfAbsent(price, k -> new LinkedList<>());
        ordersAtPrice.add(order);
    }

    // 獲取最佳買單
    public Order getBestBuyOrder() {
        if (buyOrders.isEmpty()) return null;
        Map.Entry<BigDecimal, Queue<Order>> entry = buyOrders.firstEntry();
        return entry.getValue().peek();
    }

    // 獲取最佳賣單
    public Order getBestSellOrder() {
        if (sellOrders.isEmpty()) return null;
        Map.Entry<BigDecimal, Queue<Order>> entry = sellOrders.firstEntry();
        return entry.getValue().peek();
    }

    // 更新或移除訂單
    public void updateOrRemoveOrder(Order order) {
        NavigableMap<BigDecimal, Queue<Order>> orderBook = order.getSide() == Order.Side.BUY ? buyOrders : sellOrders;
        BigDecimal price = order.getPrice();
        Queue<Order> ordersAtPrice = orderBook.get(price);

        if (ordersAtPrice != null) {
            ordersAtPrice.remove(order);
            if (order.getUnfilledQuantity().compareTo(BigDecimal.ZERO) > 0) {
                // 訂單部分成交，重新加入隊列
                ordersAtPrice.add(order);
            }
            if (ordersAtPrice.isEmpty()) {
                orderBook.remove(price);
            }
        }
    }
}
