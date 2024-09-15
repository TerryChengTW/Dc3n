package com.exchange.websocket;

import com.exchange.utils.JwtUtil;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

public class JwtWebSocketHandlerDecorator extends WebSocketHandlerDecorator {

    private final JwtUtil jwtUtil;

    public JwtWebSocketHandlerDecorator(WebSocketHandler delegate, JwtUtil jwtUtil) {
        super(delegate);
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = extractToken(session);
        if (token != null) {
            System.out.println("Extracted Token: " + token);  // Debugging output
            String username = jwtUtil.extractUsername(token);
            if (username != null && !jwtUtil.isTokenExpired(token)) {
                String userId = jwtUtil.extractUserId(token);
                session.getAttributes().put("userId", userId);
                session.getAttributes().put("username", username);
                super.afterConnectionEstablished(session);
            } else {
                System.out.println("Token is invalid or expired");
                session.close(CloseStatus.POLICY_VIOLATION);
            }
        } else {
            System.out.println("No token found in the query parameters");
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    private String extractToken(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query != null) {
            String[] params = query.split("&");
            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2 && "token".equals(keyValue[0])) {
                    return keyValue[1];
                }
            }
        }
        return null;
    }
}