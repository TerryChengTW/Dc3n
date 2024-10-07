package com.exchange.websocket;

import com.exchange.dto.OrderDTO;
import com.exchange.service.UserOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserOrderWebSocketHandler extends TextWebSocketHandler {

    private final ConcurrentHashMap<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final UserOrderService userOrderService;
    private final ObjectMapper objectMapper;

    @Autowired
    public UserOrderWebSocketHandler(UserOrderService userOrderService, ObjectMapper objectMapper) {
        this.userOrderService = userOrderService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String userId = getUserIdFromSession(session);
//        System.out.println("UserOrderWebSocket connection established for user: " + userId + ", sessionId: " + session.getId());
        userSessions.put(userId, session);

        // 發送快照
        List<OrderDTO> orders = userOrderService.getUserOrders(userId);
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("type", "snapshot");
        snapshot.put("data", orders);
        String snapshotJson = objectMapper.writeValueAsString(snapshot);
        sendMessageToUser(userId, snapshotJson);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = getUserIdFromSession(session);
//        System.out.println("UserOrderWebSocket connection closed for user: " + userId);
        userSessions.remove(userId);
    }

    public void sendOrderNotification(String userId, String eventType, OrderDTO order) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "update");
        notification.put("eventType", eventType);
        notification.put("data", order);

        try {
            String message = objectMapper.writeValueAsString(notification);
            sendMessageToUser(userId, message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMessageToUser(String userId, String message) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getUserIdFromSession(WebSocketSession session) {
        return (String) session.getAttributes().get("userId");
    }
}