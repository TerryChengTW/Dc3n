package com.exchange.repository;

import com.exchange.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TradeRepository extends JpaRepository<Trade, String> {
    // 根據需要添加其他查詢方法
}
