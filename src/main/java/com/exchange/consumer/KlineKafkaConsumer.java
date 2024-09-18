package com.exchange.consumer;

import com.exchange.model.MarketData;
import com.exchange.websocket.KlineWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class KlineKafkaConsumer {

    @Autowired
    private KlineWebSocketHandler klineWebSocketHandler;

    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(topics = "kline-updates", groupId = "kline-updates-group")
    public void consume(String message) {
        try {
            // 手動解析簡單的 JSON 結構
            var jsonNode = objectMapper.readTree(message);
            String symbol = jsonNode.get("symbol").asText();
            BigDecimal price = new BigDecimal(jsonNode.get("price").asText());

            // 直接使用 symbol 和 price 進行推送
            klineWebSocketHandler.broadcastKlineUpdate(symbol, price);
        } catch (Exception e) {
            // 處理異常
            e.printStackTrace();
        }
    }
}
