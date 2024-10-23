package com.exchange.repository;

import com.exchange.dto.SimpleTradeInfo;
import com.exchange.model.Trade;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, String> {
    List<Trade> findBySymbolOrderByTradeTimeDesc(String symbol, PageRequest pageRequest);

    List<Trade> findBySymbolAndTradeTimeBetween(String symbol, Instant startTime, Instant endTime);

    @Query("SELECT new com.exchange.dto.SimpleTradeInfo(t.price, t.quantity, t.tradeTime, " +
            "CASE WHEN t.takerOrderId = :orderId THEN 'TAKER' ELSE 'MAKER' END) " +
            "FROM Trade t WHERE t.buyOrder.id = :orderId OR t.sellOrder.id = :orderId " +
            "ORDER BY t.tradeTime")
    List<SimpleTradeInfo> findSimpleTradeInfoByOrderId(@Param("orderId") String orderId);

    List<Trade> findByBuyOrderIdOrSellOrderId(String buyOrderId, String sellOrderId);
}