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
            // 1. 聚合1分鐘數據
            MarketData oneMinuteData = aggregateOneMinuteData(symbol, startTime, endTime);
            if (oneMinuteData != null) {
                marketDataRepository.save(oneMinuteData);
            }

            // 2. 檢查並聚合5分鐘數據
            if (now.getEpochSecond() % (5 * 60) == 0) {
                aggregateAndSaveAggregatedData(symbol, "5m", 5);
            }

            // 3. 檢查並聚合1小時數據
            if (now.getEpochSecond() % (60 * 60) == 0) {
                aggregateAndSaveAggregatedData(symbol, "1h", 60);
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

    // 聚合多時間框架數據（5分鐘、1小時等）
    private void aggregateAndSaveAggregatedData(String symbol, String timeFrame, int minutes) {
        Instant endTime = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Instant startTime = endTime.minus(minutes, ChronoUnit.MINUTES);

        List<MarketData> oneMinuteData = marketDataRepository.findBySymbolAndTimeFrameAndTimestampBetween(symbol, "1m", startTime, endTime);

        if (oneMinuteData.isEmpty()) {
            return;
        }

        BigDecimal open = oneMinuteData.get(0).getOpen();
        BigDecimal close = oneMinuteData.get(oneMinuteData.size() - 1).getClose();
        BigDecimal high = oneMinuteData.stream().map(MarketData::getHigh).max(BigDecimal::compareTo).orElse(open);
        BigDecimal low = oneMinuteData.stream().map(MarketData::getLow).min(BigDecimal::compareTo).orElse(open);
        BigDecimal volume = oneMinuteData.stream().map(MarketData::getVolume).reduce(BigDecimal.ZERO, BigDecimal::add);

        MarketData aggregatedData = new MarketData();
        aggregatedData.setSymbol(symbol);
        aggregatedData.setTimeFrame(timeFrame);
        aggregatedData.setTimestamp(startTime);
        aggregatedData.setOpen(open);
        aggregatedData.setHigh(high);
        aggregatedData.setLow(low);
        aggregatedData.setClose(close);
        aggregatedData.setVolume(volume);

        marketDataRepository.save(aggregatedData);
    }
}
