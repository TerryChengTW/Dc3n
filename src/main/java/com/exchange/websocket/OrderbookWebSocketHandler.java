package com.exchange.websocket;

import com.exchange.service.OrderbookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OrderbookWebSocketHandler extends TextWebSocketHandler {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> symbolSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final OrderbookService orderbookService;

    public OrderbookWebSocketHandler(ObjectMapper objectMapper, OrderbookService orderbookService) {
        this.objectMapper = objectMapper;
        this.orderbookService = orderbookService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String symbol = getSymbolFromSession(session);
        if (symbol != null) {
            symbolSessions.computeIfAbsent(symbol, k -> ConcurrentHashMap.newKeySet()).add(session);
            sendOrderbookSnapshot(session, symbol);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
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

    public void broadcastOrderbookUpdate(String symbol, Object update) {
        String updateJson;
        try {
            updateJson = objectMapper.writeValueAsString(update);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        Set<WebSocketSession> sessions = symbolSessions.get(symbol);
        if (sessions != null) {
            sessions.forEach(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(updateJson));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private void sendOrderbookSnapshot(WebSocketSession session, String symbol) throws IOException {
        Map<String, Object> snapshot = orderbookService.getOrderbookSnapshot(symbol);
        String snapshotJson = objectMapper.writeValueAsString(snapshot);
        session.sendMessage(new TextMessage(snapshotJson));
    }

    private String getSymbolFromSession(WebSocketSession session) {
        return (String) session.getAttributes().get("symbol");
    }
}