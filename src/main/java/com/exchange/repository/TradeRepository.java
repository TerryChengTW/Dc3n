package com.exchange.repository;

import com.exchange.model.Trade;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, String> {
    List<Trade> findBySymbolOrderByTradeTimeDesc(String symbol, PageRequest pageRequest);
}