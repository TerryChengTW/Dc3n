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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
        session.sendMessage(new TextMessage(historicalMessage));

//        // 查詢當前未結束的K線數據（假設1分鐘K線）
//        List<Trade> currentTrades = tradeRepository.findBySymbolAndTradeTimeBetween("BTCUSDT", getStartOfMinute(), getEndOfMinute());
//
//        BigDecimal highPrice = currentTrades.stream().map(Trade::getPrice).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
//        BigDecimal lowPrice = currentTrades.stream().map(Trade::getPrice).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
//        BigDecimal currentPrice = currentTrades.isEmpty() ? BigDecimal.ZERO : currentTrades.get(currentTrades.size() - 1).getPrice();
//
//        // 使用當前時間戳作為未結束K線的時間
//        long currentTime = System.currentTimeMillis() / 1000;
//
//        // 構建當前K線數據的消息並發送
//        String currentKlineMessage = "{\"symbol\": \"BTCUSDT\", \"high\": " + highPrice + ", \"low\": " + lowPrice + ", \"close\": " + currentPrice + ", \"time\": " + currentTime + "}";
//        session.sendMessage(new TextMessage(currentKlineMessage));
    }

    private String createHistoricalDataMessage(List<MarketData> historicalData) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // 手動轉換時間為 Unix 時間戳
        List<Map<String, Object>> historicalDataWithTimestamps = new ArrayList<>();
        for (MarketData data : historicalData) {
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("symbol", data.getSymbol());
            dataMap.put("open", data.getOpen());
            dataMap.put("high", data.getHigh());
            dataMap.put("low", data.getLow());
            dataMap.put("close", data.getClose());
            // 將 LocalDateTime 轉換為 Unix 時間戳
            dataMap.put("time", data.getTimestamp().toEpochSecond(ZoneOffset.UTC));
            historicalDataWithTimestamps.add(dataMap);
        }

        try {
            // 將轉換後的數據轉換為 JSON
            return objectMapper.writeValueAsString(historicalDataWithTimestamps);
        } catch (Exception e) {
            e.printStackTrace();
            return "[]"; // 如果轉換失敗，返回空數組
        }
    }


    // Helper methods to get start and end of the current minute
    private LocalDateTime getStartOfMinute() {
        return LocalDateTime.now().withSecond(0).withNano(0);
    }

    private LocalDateTime getEndOfMinute() {
        return LocalDateTime.now().withSecond(59).withNano(999999999);
    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
    }

    public void broadcastKlineUpdate(String symbol, BigDecimal price) {
        sessions.forEach(session -> {
            try {
                // 構建簡單的消息結構
                String message = "{\"symbol\": \"" + symbol + "\", \"price\": " + price + "}";
                session.sendMessage(new TextMessage(message));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
