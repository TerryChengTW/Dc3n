package com.exchange.service;

import com.exchange.model.MarketData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class MarketDataInitializationService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Random random = new Random();
    private static final int DEFAULT_BATCH_SIZE = 1000; // 調整為 5000 進行測試
    private static final int THREADS = 32; // 線程數，根據 CPU 進行測試和調整
    private static final ExecutorService executorService = Executors.newFixedThreadPool(THREADS);

    @PostConstruct
    public void initializeMarketData() {
        String symbol = "BTCUSDT";
        Instant endTime = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Instant lastDataTime = getLastDataTime(symbol);
        Instant startTime = lastDataTime != null ? lastDataTime.plus(1, ChronoUnit.MINUTES) : endTime.minus(300, ChronoUnit.HOURS);

        if (startTime.isAfter(endTime)) {
            System.out.println("數據已經是最新的，無需初始化。");
            return;
        }

        BigDecimal lastPrice = lastDataTime != null ? getLastPrice(symbol, lastDataTime) : BigDecimal.valueOf(50000);

        long totalMinutes = ChronoUnit.MINUTES.between(startTime, endTime);
        long minutesPerThread = totalMinutes / THREADS;

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            Instant threadStartTime = startTime.plus(minutesPerThread * i, ChronoUnit.MINUTES);
            Instant threadEndTime = i == THREADS - 1 ? endTime : threadStartTime.plus(minutesPerThread, ChronoUnit.MINUTES);
            BigDecimal threadInitialPrice = i == 0 ? lastPrice : getLastPrice(symbol, threadStartTime.minus(1, ChronoUnit.MINUTES));

            futures.add(CompletableFuture.runAsync(() -> generateAndInsertData(symbol, threadStartTime, threadEndTime, threadInitialPrice), executorService));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        System.out.println("市場數據初始化完成，從 " + startTime + " 到 " + endTime);
    }

    public void generateAndInsertData(String symbol, Instant startTime, Instant endTime, BigDecimal initialPrice) {
        String sql = "INSERT INTO market_data (symbol, time_frame, timestamp, open, high, low, close, volume) VALUES (?,?,?,?,?,?,?,?)";
        List<MarketData> batch = new ArrayList<>();
        BigDecimal lastPrice = initialPrice;

        for (Instant time = startTime; time.isBefore(endTime); time = time.plus(1, ChronoUnit.MINUTES)) {
            MarketData data = generateMarketData(symbol, time, lastPrice);
            batch.add(data);
            lastPrice = data.getClose();

            if (batch.size() >= DEFAULT_BATCH_SIZE) {
                batchInsert(sql, batch);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            batchInsert(sql, batch);
        }
    }

    private void batchInsert(String sql, List<MarketData> batch) {
        jdbcTemplate.batchUpdate(sql, batch, DEFAULT_BATCH_SIZE, (PreparedStatement ps, MarketData data) -> {
            ps.setString(1, data.getSymbol());
            ps.setString(2, data.getTimeFrame());
            ps.setTimestamp(3, Timestamp.from(data.getTimestamp().atOffset(ZoneOffset.UTC).toInstant()));
            ps.setBigDecimal(4, data.getOpen());
            ps.setBigDecimal(5, data.getHigh());
            ps.setBigDecimal(6, data.getLow());
            ps.setBigDecimal(7, data.getClose());
            ps.setBigDecimal(8, data.getVolume());
        });
    }

    private Instant getLastDataTime(String symbol) {
        try {
            String sql = "SELECT MAX(timestamp) FROM market_data WHERE symbol = ?";
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Timestamp timestamp = rs.getTimestamp(1);
                return timestamp != null ? timestamp.toInstant() : null; // 確認 timestamp 不為 null 才調用 toInstant()
            }, symbol);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private BigDecimal getLastPrice(String symbol, Instant time) {
        String sql = "SELECT close FROM market_data WHERE symbol = ? AND timestamp = ?";
        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> rs.getBigDecimal(1), symbol, time);
        } catch (EmptyResultDataAccessException e) {
            return BigDecimal.valueOf(50000);
        }
    }

    private MarketData generateMarketData(String symbol, Instant time, BigDecimal lastPrice) {
        BigDecimal maxChange = lastPrice.multiply(BigDecimal.valueOf(0.005));
        BigDecimal change = maxChange.multiply(BigDecimal.valueOf(random.nextDouble() * 2 - 1));

        BigDecimal open = lastPrice;
        BigDecimal close = lastPrice.add(change).max(BigDecimal.valueOf(30000)).min(BigDecimal.valueOf(70000));
        BigDecimal high = open.max(close).add(maxChange.multiply(BigDecimal.valueOf(random.nextDouble())));
        BigDecimal low = open.min(close).subtract(maxChange.multiply(BigDecimal.valueOf(random.nextDouble())));
        BigDecimal volume = BigDecimal.valueOf(random.nextDouble() * 100);

        MarketData data = new MarketData();
        data.setSymbol(symbol);
        data.setTimeFrame("1m");
        data.setTimestamp(time);
        data.setOpen(open);
        data.setHigh(high);
        data.setLow(low);
        data.setClose(close);
        data.setVolume(volume);

        return data;
    }
}
