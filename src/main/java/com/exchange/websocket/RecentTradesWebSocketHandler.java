package com.exchange.websocket;

import com.exchange.model.Trade;
import com.exchange.service.TradeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RecentTradesWebSocketHandler extends TextWebSocketHandler {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> symbolSessions = new ConcurrentHashMap<>();

    @Autowired
    private ObjectMapper objectMapper;

    private final TradeService tradeService;

    public RecentTradesWebSocketHandler(TradeService tradeService, ObjectMapper objectMapper) {
        this.tradeService = tradeService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String symbol = extractSymbolFromUri(session.getUri().toString());
        session.getAttributes().put("symbol", symbol); // 設置到 session

        symbolSessions.computeIfAbsent(symbol, k -> ConcurrentHashMap.newKeySet()).add(session);

        // 獲取最近的六筆成交記錄
        List<Trade> recentTrades = tradeService.getRecentTrades(symbol, 6);

        // 反轉資料順序，確保最新的交易在最前面
        Collections.reverse(recentTrades);

        // 發送最近的六筆成交記錄
        sendRecentTrades(session, recentTrades);
    }


    private String extractSymbolFromUri(String uri) {
        String[] parts = uri.split("/");
        return parts[parts.length - 1].split("\\?")[0]; // 提取交易對
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String symbol = getSymbolFromSession(session);
        if (symbol != null) {
            Set<WebSocketSession> sessions = symbolSessions.get(symbol);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    symbolSessions.remove(symbol);
                }
            }
        }
    }

    public void broadcastRecentTrade(String symbol, Object tradeData) {
        String tradeJson;
        try {
            tradeJson = objectMapper.writeValueAsString(tradeData);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        Set<WebSocketSession> sessions = symbolSessions.get(symbol);
        if (sessions != null) {
            sessions.forEach(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(tradeJson));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    public void sendRecentTrades(WebSocketSession session, List<Trade> recentTrades) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(recentTrades)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getSymbolFromSession(WebSocketSession session) {
        return (String) session.getAttributes().get("symbol");
    }
}
