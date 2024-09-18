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
import java.time.LocalDateTime;
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
    private static final int DEFAULT_BATCH_SIZE = 5000; // 調整為 5000 進行測試
    private static final int THREADS = 32; // 線程數，根據 CPU 進行測試和調整
    private static final ExecutorService executorService = Executors.newFixedThreadPool(THREADS);

    @PostConstruct
    public void initializeMarketData() {
        String symbol = "BTCUSDT";
        LocalDateTime endTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime lastDataTime = getLastDataTime(symbol);
        LocalDateTime startTime = lastDataTime != null ? lastDataTime.plusMinutes(1) : endTime.minusHours(300);

        if (startTime.isAfter(endTime)) {
            System.out.println("數據已經是最新的，無需初始化。");
            return;
        }

        BigDecimal lastPrice = lastDataTime != null ? getLastPrice(symbol, lastDataTime) : BigDecimal.valueOf(50000);

        long totalMinutes = ChronoUnit.MINUTES.between(startTime, endTime);
        long minutesPerThread = totalMinutes / THREADS;

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            LocalDateTime threadStartTime = startTime.plusMinutes(minutesPerThread * i);
            LocalDateTime threadEndTime = i == THREADS - 1 ? endTime : threadStartTime.plusMinutes(minutesPerThread);
            BigDecimal threadInitialPrice = i == 0 ? lastPrice : getLastPrice(symbol, threadStartTime.minusMinutes(1));

            futures.add(CompletableFuture.runAsync(() -> generateAndInsertData(symbol, threadStartTime, threadEndTime, threadInitialPrice), executorService));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        System.out.println("市場數據初始化完成，從 " + startTime + " 到 " + endTime);
    }

    public void generateAndInsertData(String symbol, LocalDateTime startTime, LocalDateTime endTime, BigDecimal initialPrice) {
        String sql = "INSERT INTO market_data (symbol, time_frame, timestamp, open, high, low, close, volume) VALUES (?,?,?,?,?,?,?,?)";
        List<MarketData> batch = new ArrayList<>();
        BigDecimal lastPrice = initialPrice;

        for (LocalDateTime time = startTime; time.isBefore(endTime); time = time.plusMinutes(1)) {
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
            ps.setTimestamp(3, Timestamp.valueOf(data.getTimestamp()));
            ps.setBigDecimal(4, data.getOpen());
            ps.setBigDecimal(5, data.getHigh());
            ps.setBigDecimal(6, data.getLow());
            ps.setBigDecimal(7, data.getClose());
            ps.setBigDecimal(8, data.getVolume());
        });
    }

    private LocalDateTime getLastDataTime(String symbol) {
        try {
            String sql = "SELECT MAX(timestamp) FROM market_data WHERE symbol = ?";
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> rs.getObject(1, LocalDateTime.class), symbol);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private BigDecimal getLastPrice(String symbol, LocalDateTime time) {
        String sql = "SELECT close FROM market_data WHERE symbol = ? AND timestamp = ?";
        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> rs.getBigDecimal(1), symbol, time);
        } catch (EmptyResultDataAccessException e) {
            return BigDecimal.valueOf(50000);
        }
    }

    private MarketData generateMarketData(String symbol, LocalDateTime time, BigDecimal lastPrice) {
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
