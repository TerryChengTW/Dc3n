package com.exchange.repository;

import com.exchange.model.Order;
import com.exchange.model.Trade;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CustomTradeRepositoryImpl implements CustomTradeRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void saveAllOrdersAndTrade(Order buyOrder, Order sellOrder, Trade trade) {
        // 使用 merge 來保存或更新買單
        buyOrder = entityManager.merge(buyOrder);
        // 使用 merge 來保存或更新賣單
        sellOrder = entityManager.merge(sellOrder);

        // 立即 flush，確保 buyOrder 和 sellOrder 都已經被持久化
        entityManager.flush();

        // 保存交易
        trade.setBuyOrder(buyOrder); // 確保 trade 關聯正確的持久化實體
        trade.setSellOrder(sellOrder);
        entityManager.persist(trade);
    }
}
