package com.exchange.websocket;

import com.exchange.model.MarketData;
import com.exchange.model.Trade;
import com.exchange.repository.MarketDataRepository;
import com.exchange.repository.TradeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class KlineWebSocketHandler extends TextWebSocketHandler {

    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Autowired
    private MarketDataRepository marketDataRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);

        // 查詢歷史K線數據
        List<MarketData> historicalData = marketDataRepository.findTop500BySymbolOrderByTimestampDesc("BTCUSDT");

        // 將歷史數據轉換為簡單的JSON結構並發送
        String historicalMessage = createHistoricalDataMessage(historicalData);
        System.out.println(historicalMessage);

        session.sendMessage(new TextMessage(historicalMessage));

        // 查詢當前未結束的K線數據（假設1分鐘K線）
        Instant now = Instant.now();
        Instant startOfCurrentMinute = now.truncatedTo(ChronoUnit.MINUTES); // 當前分鐘的開始
        Instant endOfCurrentMinute = startOfCurrentMinute.plus(1, ChronoUnit.MINUTES); // 當前分鐘的結束

        List<Trade> currentTrades = tradeRepository.findBySymbolAndTradeTimeBetween("BTCUSDT", startOfCurrentMinute, endOfCurrentMinute);

        BigDecimal highPrice = BigDecimal.ZERO;
        BigDecimal lowPrice = BigDecimal.ZERO;
        BigDecimal currentPrice = BigDecimal.ZERO;
        BigDecimal openPrice = BigDecimal.ZERO;

        // 查詢上一分鐘的MarketData數據
        MarketData previousMarketData = marketDataRepository.findTopBySymbolAndTimeFrameOrderByTimestampDesc("BTCUSDT", "1m");

        if (previousMarketData != null) {
            // 使用上一分鐘的收盤價作為開盤價
            openPrice = previousMarketData.getClose();
        }

        if (currentTrades.isEmpty()) {
            // 沒有當前交易數據，使用上一分鐘的收盤價設置高、低、收盤價
            if (previousMarketData != null) {
                highPrice = previousMarketData.getClose();
                lowPrice = previousMarketData.getClose();
                currentPrice = previousMarketData.getClose();
            }
        } else {
            // 有當前交易數據，根據當前數據設置高、低、收盤價
            highPrice = currentTrades.stream().map(Trade::getPrice).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            lowPrice = currentTrades.stream().map(Trade::getPrice).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            currentPrice = currentTrades.get(currentTrades.size() - 1).getPrice();
        }

        long currentTime = now.getEpochSecond();

        // 構建當前K線數據的消息並發送
        String currentKlineMessage = "{\"type\": \"current_kline\", \"symbol\": \"BTCUSDT\", \"open\": "
                + openPrice + ", \"high\": " + highPrice + ", \"low\": " + lowPrice + ", \"close\": " + currentPrice + ", \"time\": " + currentTime + "}";
        System.out.println("currentKlineMessage: ");
        System.out.println(currentKlineMessage);
        session.sendMessage(new TextMessage(currentKlineMessage));
    }

    private String createHistoricalDataMessage(List<MarketData> historicalData) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        List<Map<String, Object>> historicalDataWithTimestamps = new ArrayList<>();
        for (MarketData data : historicalData) {
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("type", "historical");  // 添加 type 標識
            dataMap.put("symbol", data.getSymbol());
            dataMap.put("open", data.getOpen());
            dataMap.put("high", data.getHigh());
            dataMap.put("low", data.getLow());
            dataMap.put("close", data.getClose());
            dataMap.put("time", data.getTimestamp().getEpochSecond());  // Unix 時間戳
            historicalDataWithTimestamps.add(dataMap);
        }

        try {
            return objectMapper.writeValueAsString(historicalDataWithTimestamps);
        } catch (Exception e) {
            e.printStackTrace();
            return "[]"; // 如果轉換失敗，返回空數組
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
    }

    public void broadcastKlineUpdate(String symbol, BigDecimal price, Instant tradeTime) {
        sessions.forEach(session -> {
            try {
                // 構建實時成交數據的消息，包含時間戳
                String message = "{\"type\": \"trade\", \"symbol\": \"" + symbol + "\", \"price\": " + price + ", \"time\": " + tradeTime.getEpochSecond() + "}";
                session.sendMessage(new TextMessage(message));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

}
