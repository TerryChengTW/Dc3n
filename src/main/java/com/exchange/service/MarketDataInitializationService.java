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
    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final int THREADS = 32;
    private static final ExecutorService executorService = Executors.newFixedThreadPool(THREADS);

    @PostConstruct
    public void initializeMarketData() {
        initializeSymbolMarketData("BTCUSDT");
        initializeSymbolMarketData("ETHUSDT");  // 加入 ETHUSDT 的初始化邏輯
    }

    public void initializeSymbolMarketData(String symbol) {
        Instant endTime = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Instant lastDataTime = getLastDataTime(symbol);
        Instant startTime = lastDataTime != null ? lastDataTime.plus(1, ChronoUnit.MINUTES) : endTime.minus(1000, ChronoUnit.HOURS);

        if (startTime.isAfter(endTime)) {
            System.out.println(symbol + " 的數據已經是最新的，無需初始化。");
            return;
        }

        // 根據不同幣種設置初始價格範圍
        BigDecimal lastPrice = lastDataTime != null ? getLastPrice(symbol, lastDataTime) :
                (symbol.equals("BTCUSDT") ? BigDecimal.valueOf(50000) : BigDecimal.valueOf(4000));  // ETHUSDT 設置初始為4000

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

        System.out.println(symbol + " 市場數據初始化完成，從 " + startTime + " 到 " + endTime);

        // 在所有1分鐘數據生成後，手動觸發5分鐘和1小時聚合
        aggregateData(symbol, startTime, endTime, "5m", 5);
        aggregateData(symbol, startTime, endTime, "1h", 60);
    }

    private void aggregateData(String symbol, Instant startTime, Instant endTime, String timeFrame, int minutes) {
        List<MarketData> batch = new ArrayList<>();
        String sql = "INSERT INTO market_data (symbol, time_frame, timestamp, open, high, low, close, volume) VALUES (?,?,?,?,?,?,?,?)";

        // 獲取當前時間，並確認正在進行中的時間範圍不應該生成
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        // 對5分鐘K線或1小時K線做特別處理
        if (timeFrame.equals("5m")) {
            // 如果當前時間還沒到完整的5分鐘邊界（比如12:59時），則不應該生成12:55的5分鐘K線
            if (now.getEpochSecond() % (5 * 60) != 0) {
                endTime = now.minus(minutes, ChronoUnit.MINUTES); // 調整endTime，避免生成未完成的5分鐘數據
            }
        } else if (timeFrame.equals("1h")) {
            // 如果當前時間還沒到整點（比如12:59時），則不應該生成12:00的1小時K線
            if (now.getEpochSecond() % (60 * 60) != 0) {
                endTime = now.minus(minutes, ChronoUnit.MINUTES); // 調整endTime，避免生成未完成的1小時數據
            }
        }

        // 確保時間區間對齊到正確的過去區間
        startTime = startTime.truncatedTo(ChronoUnit.MINUTES)
                .minus(startTime.getEpochSecond() % (minutes * 60), ChronoUnit.SECONDS);

        // 確保在正確時間範圍內進行聚合
        for (Instant time = startTime; time.isBefore(endTime); time = time.plus(minutes, ChronoUnit.MINUTES)) {
            Instant nextTime = time.plus(minutes, ChronoUnit.MINUTES);

            // 查詢這個時間段內的1分鐘數據
            List<MarketData> oneMinuteData = jdbcTemplate.query(
                    "SELECT * FROM market_data WHERE symbol = ? AND time_frame = '1m' AND timestamp >= ? AND timestamp < ?",
                    new Object[]{symbol, Timestamp.from(time), Timestamp.from(nextTime)},
                    (rs, rowNum) -> {
                        MarketData data = new MarketData();
                        data.setSymbol(rs.getString("symbol"));
                        data.setTimeFrame(rs.getString("time_frame"));
                        data.setTimestamp(rs.getTimestamp("timestamp").toInstant());
                        data.setOpen(rs.getBigDecimal("open"));
                        data.setHigh(rs.getBigDecimal("high"));
                        data.setLow(rs.getBigDecimal("low"));
                        data.setClose(rs.getBigDecimal("close"));
                        data.setVolume(rs.getBigDecimal("volume"));
                        return data;
                    }
            );

            // 檢查是否有足夠的 1 分鐘數據
            if (oneMinuteData.isEmpty()) {
                continue;
            }

            // 聚合數據
            BigDecimal open = oneMinuteData.get(0).getOpen();
            BigDecimal close = oneMinuteData.get(oneMinuteData.size() - 1).getClose();
            BigDecimal high = oneMinuteData.stream().map(MarketData::getHigh).max(BigDecimal::compareTo).orElse(open);
            BigDecimal low = oneMinuteData.stream().map(MarketData::getLow).min(BigDecimal::compareTo).orElse(open);
            BigDecimal volume = oneMinuteData.stream().map(MarketData::getVolume).reduce(BigDecimal.ZERO, BigDecimal::add);

            // 創建聚合數據
            MarketData aggregatedData = new MarketData();
            aggregatedData.setSymbol(symbol);
            aggregatedData.setTimeFrame(timeFrame);
            aggregatedData.setTimestamp(time); // 這裡是對齊後的整數 5 分鐘或 1 小時時間
            aggregatedData.setOpen(open);
            aggregatedData.setHigh(high);
            aggregatedData.setLow(low);
            aggregatedData.setClose(close);
            aggregatedData.setVolume(volume);

            // 將聚合數據加入批量列表
            batch.add(aggregatedData);

            // 如果達到批量插入的閾值，則執行批量插入
            if (batch.size() >= DEFAULT_BATCH_SIZE) {
                batchInsert(sql, batch);
                batch.clear();
            }
        }

        // 插入剩餘未達到批量閾值的數據
        if (!batch.isEmpty()) {
            batchInsert(sql, batch);
        }
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
            // 根據 symbol 設定不同的預設值
            return symbol.equals("ETHUSDT") ? BigDecimal.valueOf(4000) : BigDecimal.valueOf(50000);
        }
    }

    private MarketData generateMarketData(String symbol, Instant time, BigDecimal lastPrice) {
        BigDecimal maxChange = lastPrice.multiply(BigDecimal.valueOf(0.005));
        BigDecimal change = maxChange.multiply(BigDecimal.valueOf(random.nextDouble() * 2 - 1));

        BigDecimal open = lastPrice;
        BigDecimal close, high, low;

        if (symbol.equals("BTCUSDT")) {
            // BTCUSDT 的價格範圍設置在 30000 到 70000 之間
            close = lastPrice.add(change).max(BigDecimal.valueOf(30000)).min(BigDecimal.valueOf(70000));
            high = open.max(close).add(maxChange.multiply(BigDecimal.valueOf(random.nextDouble())));
            low = open.min(close).subtract(maxChange.multiply(BigDecimal.valueOf(random.nextDouble())));
        } else if (symbol.equals("ETHUSDT")) {
            // ETHUSDT 的價格範圍設置在 2000 到 6000 之間
            close = lastPrice.add(change).max(BigDecimal.valueOf(2000)).min(BigDecimal.valueOf(6000));
            high = open.max(close).add(maxChange.multiply(BigDecimal.valueOf(random.nextDouble())));
            low = open.min(close).subtract(maxChange.multiply(BigDecimal.valueOf(random.nextDouble())));
        } else {
            // 其他幣種的價格範圍可以根據需求設置
            close = lastPrice.add(change);
            high = open.max(close).add(maxChange.multiply(BigDecimal.valueOf(random.nextDouble())));
            low = open.min(close).subtract(maxChange.multiply(BigDecimal.valueOf(random.nextDouble())));
        }

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
