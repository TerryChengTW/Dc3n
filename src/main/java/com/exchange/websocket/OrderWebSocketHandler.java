//package com.exchange.websocket;
//
//import org.springframework.web.socket.TextMessage;
//import org.springframework.web.socket.WebSocketSession;
//import org.springframework.web.socket.handler.TextWebSocketHandler;
//
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.Map;
//
//public class OrderWebSocketHandler extends TextWebSocketHandler {
//
//    // 保存每個用戶的 WebSocket 連接
//    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();
//
//    @Override
//    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
//        // 假設用戶 ID 是通過 WebSocket URL 中的參數傳入
//        String userId = session.getUri().getQueryParameter("userId");
//        userSessions.put(userId, session);
//    }
//
//    @Override
//    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
//        // 處理從前端傳來的消息（如需要）
//    }
//
//    public void sendOrderUpdate(String userId, String orderUpdate) {
//        WebSocketSession session = userSessions.get(userId);
//        if (session != null && session.isOpen()) {
//            session.sendMessage(new TextMessage(orderUpdate));
//        }
//    }
//}
