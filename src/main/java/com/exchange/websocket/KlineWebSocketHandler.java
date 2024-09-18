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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
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

        // 查詢當前未結束的K線數據（假設1分鐘K線）
        List<Trade> currentTrades = tradeRepository.findBySymbolAndTradeTimeBetween("BTCUSDT", getStartOfMinute(), getEndOfMinute());

        BigDecimal highPrice = currentTrades.stream().map(Trade::getPrice).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal lowPrice = currentTrades.stream().map(Trade::getPrice).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal currentPrice = currentTrades.isEmpty() ? BigDecimal.ZERO : currentTrades.get(currentTrades.size() - 1).getPrice();

        // 構建當前K線數據的消息並發送
        String currentKlineMessage = "{\"symbol\": \"BTCUSDT\", \"high\": " + highPrice + ", \"low\": " + lowPrice + ", \"current\": " + currentPrice + "}";
        session.sendMessage(new TextMessage(currentKlineMessage));
    }

    private String createHistoricalDataMessage(List<MarketData> historicalData) {
        StringBuilder messageBuilder = new StringBuilder("[");
        for (MarketData data : historicalData) {
            messageBuilder.append("{\"symbol\":\"").append(data.getSymbol()).append("\",")
                    .append("\"open\":").append(data.getOpen()).append(",")
                    .append("\"high\":").append(data.getHigh()).append(",")
                    .append("\"low\":").append(data.getLow()).append(",")
                    .append("\"close\":").append(data.getClose()).append(",")
                    // 使用 ZoneOffset.UTC 或其他你需要的時區來獲取秒級時間戳
                    .append("\"time\":").append(data.getTimestamp().toEpochSecond(ZoneOffset.ofHours(8))).append("},");
        }
        // 移除最後一個逗號並關閉JSON數組
        if (messageBuilder.length() > 1) {
            messageBuilder.setLength(messageBuilder.length() - 1);
        }
        messageBuilder.append("]");
        return messageBuilder.toString();
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
