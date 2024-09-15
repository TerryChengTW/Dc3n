package com.exchange.config;

import com.exchange.websocket.JwtWebSocketHandlerDecorator;
import com.exchange.websocket.WebSocketHandler;
import com.exchange.utils.JwtUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketHandler webSocketHandler;
    private final JwtUtil jwtUtil;

    public WebSocketConfig(WebSocketHandler webSocketHandler, JwtUtil jwtUtil) {
        this.webSocketHandler = webSocketHandler;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new JwtWebSocketHandlerDecorator(webSocketHandler, jwtUtil), "/ws")
                .setAllowedOrigins("*");
    }
}