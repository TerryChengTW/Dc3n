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
    @Transactional // 確保在同一事務內
    public void saveAllOrdersAndTrade(Order buyOrder, Order sellOrder, Trade trade) {
        // 更新或保存買單
        entityManager.merge(buyOrder);
        // 更新或保存賣單
        entityManager.merge(sellOrder);

        // 立即將 buyOrder 和 sellOrder 插入或更新到數據庫中
        entityManager.flush();

        // 保存交易
        entityManager.persist(trade);
    }
}
