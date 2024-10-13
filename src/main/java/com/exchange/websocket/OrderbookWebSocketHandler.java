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
import java.util.*;
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

        // 預先初始化 Kafka 消費者，訂閱所有需要的 symbol topic
        initializeKafkaSubscriptions();
    }

    private void initializeKafkaSubscriptions() {
        // 訂閱所需的 symbol 列表
        String[] symbols = {"BTCUSDT", "ETHUSDT"};

        for (String symbol : symbols) {
            subscriptionManager.subscribeToSymbol(symbol, record -> {
                String deltaMessage = record.value();
                sendDeltaToWebSocket(symbol, deltaMessage);
            });
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String symbol = extractSymbolFromUrl(session);
        BigDecimal interval = extractIntervalFromUrl(session);

        if (symbol != null) {
//            System.out.println("OrderbookWebSocket connection established for symbol: " + symbol + ", sessionId: " + session.getId());
            session.getAttributes().put("symbol", symbol); // 將 symbol 設置到 session 屬性中
            session.getAttributes().put("interval", interval); // 將 interval 設置到 session 屬性中
            symbolSessions.computeIfAbsent(symbol, k -> ConcurrentHashMap.newKeySet()).add(session);

            // 發送訂單簿快照
            sendOrderbookSnapshot(session, symbol, interval);
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
                }
            }
        }
    }

    private void sendDeltaToWebSocket(String symbol, String deltaMessage) {
        Set<WebSocketSession> sessions = symbolSessions.get(symbol);

        // 添加日誌以查看增量消息內容和符號
//        System.out.println("Sending delta to symbol: " + symbol + ", message: " + deltaMessage);

        if (sessions != null && !sessions.isEmpty()) {
            for (WebSocketSession session : sessions) {
                try {
                    session.sendMessage(new TextMessage(deltaMessage));
//                    System.out.println("Message sent to session: " + session.getId());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
//            System.out.println("No WebSocket sessions available for symbol: " + symbol);
        }
    }


    private Map<String, String> extractQueryParams(WebSocketSession session) {
        Map<String, String> queryParams = new HashMap<>();
        String query = Objects.requireNonNull(session.getUri()).getQuery();
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

    private void sendOrderbookSnapshot(WebSocketSession session, String symbol, BigDecimal interval) throws IOException {
        // 獲取 orderbook snapshot，返回值為 Map<String, List<OrderbookSnapshot>>
        Map<String, List<OrderbookSnapshotService.OrderbookSnapshot>> snapshot = orderbookService.getOrderbookSnapshot(symbol, interval);

        // 將快照結果轉換為 JSON 字符串
        String snapshotJson = objectMapper.writeValueAsString(snapshot);
        System.out.println("Orderbook snapshot for symbol: " + symbol + ", snapshot: " + snapshotJson);

        // 發送給 WebSocket 客戶端
        session.sendMessage(new TextMessage(snapshotJson));
    }


    private String getSymbolFromSession(WebSocketSession session) {
        return (String) session.getAttributes().get("symbol");
    }
}
