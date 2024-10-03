package com.exchange.websocket;

import com.exchange.consumer.OrderBookDeltaSubscriptionManager;
import com.exchange.service.OrderbookSnapshotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OrderbookWebSocketHandler extends TextWebSocketHandler {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> symbolSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final OrderbookSnapshotService orderbookService;
    private final OrderBookDeltaSubscriptionManager subscriptionManager;

    public OrderbookWebSocketHandler(ObjectMapper objectMapper,
                                     OrderbookSnapshotService orderbookService,
                                     OrderBookDeltaSubscriptionManager subscriptionManager) {
        this.objectMapper = objectMapper;
        this.orderbookService = orderbookService;
        this.subscriptionManager = subscriptionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String symbol = extractSymbolFromUrl(session);
        BigDecimal interval = extractIntervalFromUrl(session);

        if (symbol != null) {
            System.out.println("WebSocket connection established for symbol: " + symbol + ", sessionId: " + session.getId());
            session.getAttributes().put("symbol", symbol); // 將 symbol 設置到 session 屬性中
            session.getAttributes().put("interval", interval); // 將 interval 設置到 session 屬性中
            symbolSessions.computeIfAbsent(symbol, k -> ConcurrentHashMap.newKeySet()).add(session);

            // 訂閱該 symbol 的 Kafka Topic，並推送增量消息給 WebSocket 用戶
            subscriptionManager.subscribeToSymbol(symbol, record -> {
                String deltaMessage = (String) record.value();
                sendDeltaToWebSocket(symbol, deltaMessage);
            });

            sendOrderbookSnapshot(session, symbol, interval); // 發送訂單簿快照
        } else {
            session.close(CloseStatus.POLICY_VIOLATION); // 如果沒有 symbol，則關閉連接
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

                    // 如果沒有用戶訂閱該 symbol，取消對 Kafka Topic 的訂閱
                    subscriptionManager.unsubscribeFromSymbol(symbol);
                }
            }
        }
    }

    private void sendDeltaToWebSocket(String symbol, String deltaMessage) {
        Set<WebSocketSession> sessions = symbolSessions.get(symbol);
        if (sessions != null) {
            for (WebSocketSession session : sessions) {
                try {
                    session.sendMessage(new TextMessage(deltaMessage));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private Map<String, String> extractQueryParams(WebSocketSession session) {
        Map<String, String> queryParams = new HashMap<>();
        String query = session.getUri().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2) {
                    queryParams.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return queryParams;
    }

    private String extractSymbolFromUrl(WebSocketSession session) {
        return extractQueryParams(session).get("symbol");
    }

    private BigDecimal extractIntervalFromUrl(WebSocketSession session) {
        Map<String, String> queryParams = extractQueryParams(session);
        if (queryParams.containsKey("interval")) {
            try {
                return new BigDecimal(queryParams.get("interval"));
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        return BigDecimal.valueOf(100); // Default value
    }

    private void sendOrderbookSnapshot(WebSocketSession session, String symbol, BigDecimal Interval) throws IOException {
        Map<String, Object> snapshot = orderbookService.getOrderbookSnapshot(symbol, Interval);
        System.out.println("Sending orderbook snapshot: " + snapshot);
        String snapshotJson = objectMapper.writeValueAsString(snapshot);
        session.sendMessage(new TextMessage(snapshotJson));
    }

    private String getSymbolFromSession(WebSocketSession session) {
        return (String) session.getAttributes().get("symbol");
    }
}
