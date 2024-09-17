package com.exchange.service;

import com.exchange.model.MarketData;
import com.exchange.model.Trade;
import com.exchange.repository.MarketDataRepository;
import com.exchange.repository.TradeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class MarketDataService {

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private MarketDataRepository marketDataRepository;

    @Scheduled(cron = "0 * * * * *") // 每分鐘的第0秒執行
    public void aggregateAndSaveMarketData() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime startTime = now.minusMinutes(1);
        LocalDateTime endTime = now;

        List<String> symbols = List.of("BTCUSDT", "ETHUSDT", "BNBUSDT"); // 根據需要擴展

        for (String symbol : symbols) {
            MarketData marketData = aggregateOneMinuteData(symbol, startTime, endTime);
            if (marketData != null) {
                marketDataRepository.save(marketData);
            }
        }
    }

    private MarketData aggregateOneMinuteData(String symbol, LocalDateTime startTime, LocalDateTime endTime) {
        List<Trade> trades = tradeRepository.findBySymbolAndTradeTimeBetween(symbol, startTime, endTime);

        if (trades.isEmpty()) {
            return handleNoDataSituation(symbol, startTime);
        }

        BigDecimal open = trades.get(0).getPrice();
        BigDecimal close = trades.get(trades.size() - 1).getPrice();
        BigDecimal high = trades.stream().map(Trade::getPrice).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal low = trades.stream().map(Trade::getPrice).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal volume = trades.stream().map(Trade::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);

        MarketData marketData = new MarketData();
        marketData.setSymbol(symbol);
        marketData.setTimeFrame("1m");
        marketData.setTimestamp(startTime);
        marketData.setOpen(open);
        marketData.setHigh(high);
        marketData.setLow(low);
        marketData.setClose(close);
        marketData.setVolume(volume);

        return marketData;
    }

    private MarketData handleNoDataSituation(String symbol, LocalDateTime startTime) {
        MarketData previousData = marketDataRepository.findLatestBeforeTime(symbol, startTime);
        if (previousData == null) {
            return null;
        }

        MarketData newData = new MarketData();
        newData.setSymbol(symbol);
        newData.setTimeFrame("1m");
        newData.setTimestamp(startTime);
        newData.setOpen(previousData.getClose());
        newData.setHigh(previousData.getClose());
        newData.setLow(previousData.getClose());
        newData.setClose(previousData.getClose());
        newData.setVolume(BigDecimal.ZERO);

        return newData;
    }
}