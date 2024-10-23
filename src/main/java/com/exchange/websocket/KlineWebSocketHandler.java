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

        // 提取 WebSocket 連接中的 symbol 和 timeFrame 參數
        String query = session.getUri().getQuery();
        Map<String, String> params = getQueryParams(query);
        String symbol = params.getOrDefault("symbol", "BTCUSDT");
        String timeFrame = params.getOrDefault("timeFrame", "1m");

        // 查詢對應的歷史K線數據
        List<MarketData> historicalData = marketDataRepository.findTop500BySymbolAndTimeFrameOrderByTimestampDesc(symbol, timeFrame);

        // 將歷史數據轉換為簡單的JSON結構並發送
        String historicalMessage = createHistoricalDataMessage(historicalData);
        session.sendMessage(new TextMessage(historicalMessage));

        // 根據 timeFrame 計算當前未結束的 K 棒時間範圍
        Instant now = Instant.now();
        Instant startOfCurrentKline;
        Instant endOfCurrentKline;

        switch (timeFrame) {
            case "1m":
                startOfCurrentKline = now.truncatedTo(ChronoUnit.MINUTES);
                endOfCurrentKline = startOfCurrentKline.plus(1, ChronoUnit.MINUTES);
                break;
            case "5m":
                startOfCurrentKline = now.truncatedTo(ChronoUnit.MINUTES).minus(now.getEpochSecond() % (5 * 60), ChronoUnit.SECONDS);
                endOfCurrentKline = startOfCurrentKline.plus(5, ChronoUnit.MINUTES);
                break;
            case "1h":
                startOfCurrentKline = now.truncatedTo(ChronoUnit.HOURS);
                endOfCurrentKline = startOfCurrentKline.plus(1, ChronoUnit.HOURS);
                break;
            default:
                startOfCurrentKline = now.truncatedTo(ChronoUnit.MINUTES);
                endOfCurrentKline = startOfCurrentKline.plus(1, ChronoUnit.MINUTES);
                break;
        }

        // 查詢當前未結束的 K 棒數據
        List<Trade> currentTrades = tradeRepository.findBySymbolAndTradeTimeBetween(symbol, startOfCurrentKline, endOfCurrentKline);

        BigDecimal highPrice = BigDecimal.ZERO;
        BigDecimal lowPrice = BigDecimal.ZERO;
        BigDecimal currentPrice = BigDecimal.ZERO;
        BigDecimal openPrice = BigDecimal.ZERO;

        // 查詢上一個 K 棒的 MarketData
        MarketData previousMarketData;
        switch (timeFrame) {
            case "1m":
                previousMarketData = marketDataRepository.findLatestBeforeTime(symbol, startOfCurrentKline);
                break;
            case "5m":
                previousMarketData = marketDataRepository.findTop500BySymbolAnd5mTimeFrameBeforeOrderByTimestampDesc(symbol, startOfCurrentKline).stream().findFirst().orElse(null);
                break;
            case "1h":
                previousMarketData = marketDataRepository.findTop500BySymbolAnd1hTimeFrameBeforeOrderByTimestampDesc(symbol, startOfCurrentKline).stream().findFirst().orElse(null);
                break;
            default:
                previousMarketData = marketDataRepository.findLatestBeforeTime(symbol, startOfCurrentKline);
                break;
        }

        if (previousMarketData != null) {
            openPrice = previousMarketData.getClose();
        }

        if (currentTrades.isEmpty()) {
            if (previousMarketData != null) {
                highPrice = previousMarketData.getClose();
                lowPrice = previousMarketData.getClose();
                currentPrice = previousMarketData.getClose();
            }
        } else {
            highPrice = currentTrades.stream().map(Trade::getPrice).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            lowPrice = currentTrades.stream().map(Trade::getPrice).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            currentPrice = currentTrades.get(currentTrades.size() - 1).getPrice();
        }

        long currentTime = now.getEpochSecond();

        // 發送當前K線數據
        String currentKlineMessage = "{\"type\": \"current_kline\", \"symbol\": \"" + symbol + "\", \"open\": "
                + openPrice + ", \"high\": " + highPrice + ", \"low\": " + lowPrice + ", \"close\": " + currentPrice + ", \"time\": " + currentTime + "}";
        session.sendMessage(new TextMessage(currentKlineMessage));
    }

    private Map<String, String> getQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length > 1) {
                    params.put(pair[0], pair[1]);
                }
            }
        }
        return params;
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
