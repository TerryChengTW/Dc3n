package com.exchange.service;

import com.exchange.model.MarketData;
import com.exchange.model.Trade;
import com.exchange.repository.MarketDataRepository;
import com.exchange.repository.TradeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
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
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Instant startTime = now.minus(1, ChronoUnit.MINUTES);
        Instant endTime = now;


        List<String> symbols = List.of("BTCUSDT", "ETHUSDT", "BNBUSDT"); // 根據需要擴展

        for (String symbol : symbols) {
            MarketData marketData = aggregateOneMinuteData(symbol, startTime, endTime);
            if (marketData != null) {
                marketDataRepository.save(marketData);
            }
        }
    }

    private MarketData aggregateOneMinuteData(String symbol, Instant startTime, Instant endTime) {
        List<Trade> trades = tradeRepository.findBySymbolAndTradeTimeBetween(symbol, startTime, endTime);

        BigDecimal open, close, high, low, volume;

        // 查找上一分鐘的收盤價來作為當前開盤價
        MarketData previousData = marketDataRepository.findLatestBeforeTime(symbol, startTime);
        if (previousData != null) {
            open = previousData.getClose();
        } else {
            open = BigDecimal.ZERO; // 若無前一分鐘的數據，則設為0或其他適當值
        }

        if (trades.isEmpty()) {
            // 如果沒有交易，依然使用處理無數據的方式
            return handleNoDataSituation(symbol, startTime);
        } else {
            close = trades.get(trades.size() - 1).getPrice();
            high = trades.stream().map(Trade::getPrice).max(BigDecimal::compareTo).orElse(open);
            low = trades.stream().map(Trade::getPrice).min(BigDecimal::compareTo).orElse(open);
            volume = trades.stream().map(Trade::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

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


    private MarketData handleNoDataSituation(String symbol, Instant startTime) {
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