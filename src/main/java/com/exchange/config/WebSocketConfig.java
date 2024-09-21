package com.exchange.config;

import com.exchange.websocket.*;
import com.exchange.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final RecentTradesWebSocketHandler recentTradesWebSocketHandler;
    private final KlineWebSocketHandler klineWebSocketHandler;

    public WebSocketConfig(WebSocketHandler webSocketHandler,
                           OrderbookWebSocketHandler orderbookWebSocketHandler,
                           JwtUtil jwtUtil,
                           RecentTradesWebSocketHandler recentTradesWebSocketHandler,
                           KlineWebSocketHandler klineWebSocketHandler) {
        this.webSocketHandler = webSocketHandler;
        this.orderbookWebSocketHandler = orderbookWebSocketHandler;
        this.jwtUtil = jwtUtil;
        this.recentTradesWebSocketHandler = recentTradesWebSocketHandler;
        this.klineWebSocketHandler = klineWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new JwtWebSocketHandlerDecorator(webSocketHandler, jwtUtil), "/ws")
                .setAllowedOrigins("*");
        registry.addHandler(orderbookWebSocketHandler, "/ws/orderbook")
                .setAllowedOrigins("*");
        registry.addHandler(recentTradesWebSocketHandler, "/ws/recent-trades/{symbol}")
                .setAllowedOrigins("*");
        registry.addHandler(klineWebSocketHandler, "/ws/kline").setAllowedOrigins("*");
    }
}