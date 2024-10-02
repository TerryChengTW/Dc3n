package com.exchange.consumer;

import com.exchange.model.Trade;
import com.exchange.websocket.RecentTradesWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RecentTradesKafkaConsumer {

    @Autowired
    private RecentTradesWebSocketHandler recentTradesWebSocketHandler;

    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(topics = "recent-trades", groupId = "recent-trades-group")
    public void consume(String message) {
        try {
            Trade trade = objectMapper.readValue(message, Trade.class);
            recentTradesWebSocketHandler.broadcastRecentTrade(trade.getSymbol(), trade);
        } catch (Exception e) {
            // 處理異常
            e.printStackTrace();
        }
    }
}