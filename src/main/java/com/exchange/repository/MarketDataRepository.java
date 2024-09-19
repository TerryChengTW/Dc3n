package com.exchange.repository;

import com.exchange.model.MarketData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MarketDataRepository extends JpaRepository<MarketData, String> {
    @Query("SELECT md FROM MarketData md WHERE md.symbol = :symbol AND md.timestamp = " +
            "(SELECT MAX(m.timestamp) FROM MarketData m WHERE m.symbol = :symbol AND m.timestamp < :time)")
    MarketData findLatestBeforeTime(String symbol, LocalDateTime time);

    List<MarketData> findTop500BySymbolOrderByTimestampDesc(String symbol);

    List<MarketData> findTop2BySymbolOrderByTimestampDesc(String symbol);
}
