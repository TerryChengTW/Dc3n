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
        String symbol = extractSymbol(session);
        if (token != null) {
            String username = jwtUtil.extractUsername(token);
            if (username != null && !jwtUtil.isTokenExpired(token)) {
                String userId = jwtUtil.extractUserId(token);
                session.getAttributes().put("userId", userId);
                session.getAttributes().put("username", username);
                if (symbol != null) {
                    session.getAttributes().put("symbol", symbol);
                }
                super.afterConnectionEstablished(session);
            } else {
                session.close(CloseStatus.POLICY_VIOLATION);
            }
        } else {
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    private String extractToken(WebSocketSession session) {
        String query = session.getUri().getQuery();
        return extractParameter(query, "token");
    }

    private String extractSymbol(WebSocketSession session) {
        String query = session.getUri().getQuery();
        return extractParameter(query, "symbol");
    }

    private String extractParameter(String query, String paramName) {
        if (query != null) {
            String[] params = query.split("&");
            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2 && paramName.equals(keyValue[0])) {
                    return keyValue[1];
                }
            }
        }
        return null;
    }
}