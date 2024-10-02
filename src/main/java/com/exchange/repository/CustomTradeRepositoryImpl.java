package com.exchange.repository;

import com.exchange.model.Order;
import com.exchange.model.Trade;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Repository
public class CustomTradeRepositoryImpl implements CustomTradeRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void saveAllOrdersAndTrades(List<Order> buyOrders, List<Order> sellOrders, List<Trade> trades) {
        // 批量保存買單，並收集持久化後的實例
        List<Order> persistedBuyOrders = new ArrayList<>();
        for (Order buyOrder : buyOrders) {
            Order persistedBuyOrder = entityManager.merge(buyOrder);
            persistedBuyOrders.add(persistedBuyOrder);
        }

        // 批量保存賣單，並收集持久化後的實例
        List<Order> persistedSellOrders = new ArrayList<>();
        for (Order sellOrder : sellOrders) {
            Order persistedSellOrder = entityManager.merge(sellOrder);
            persistedSellOrders.add(persistedSellOrder);
        }

        // 批量保存交易
        for (Trade trade : trades) {
            // 關聯已持久化的 buyOrder 和 sellOrder
            trade.setBuyOrder(persistedBuyOrders.stream()
                    .filter(order -> order.getId().equals(trade.getBuyOrder().getId()))
                    .findFirst()
                    .orElse(null));
            trade.setSellOrder(persistedSellOrders.stream()
                    .filter(order -> order.getId().equals(trade.getSellOrder().getId()))
                    .findFirst()
                    .orElse(null));
            entityManager.persist(trade);
        }

        // 在最後一次 flush，將所有變更一次性提交到數據庫
        entityManager.flush();
    }
}
