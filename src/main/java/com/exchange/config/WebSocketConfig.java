package com.exchange.config;

import com.exchange.websocket.JwtWebSocketHandlerDecorator;
import com.exchange.websocket.WebSocketHandler;
import com.exchange.websocket.OrderbookWebSocketHandler;
import com.exchange.utils.JwtUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketHandler webSocketHandler;
    private final OrderbookWebSocketHandler orderbookWebSocketHandler;
    private final JwtUtil jwtUtil;

    public WebSocketConfig(WebSocketHandler webSocketHandler, OrderbookWebSocketHandler orderbookWebSocketHandler, JwtUtil jwtUtil) {
        this.webSocketHandler = webSocketHandler;
        this.orderbookWebSocketHandler = orderbookWebSocketHandler;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new JwtWebSocketHandlerDecorator(webSocketHandler, jwtUtil), "/ws")
                .setAllowedOrigins("*");
        registry.addHandler(new JwtWebSocketHandlerDecorator(orderbookWebSocketHandler, jwtUtil), "/ws/orderbook")
                .setAllowedOrigins("*");
    }
}