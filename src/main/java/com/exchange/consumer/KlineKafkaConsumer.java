package com.exchange.consumer;

import com.exchange.model.MarketData;
import com.exchange.websocket.KlineWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class KlineKafkaConsumer {

    @Autowired
    private KlineWebSocketHandler klineWebSocketHandler;

    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(
            topics = "kline-updates",
            groupId = "#{T(java.util.UUID).randomUUID().toString()}",  // 動態生成唯一的 groupId
            containerFactory = "kafkaListenerContainerFactory",
            properties = {"auto.offset.reset=latest"}
    )
    public void consume(String message) {
        try {
            // 手動解析簡單的 JSON 結構
            var jsonNode = objectMapper.readTree(message);
            String symbol = jsonNode.get("symbol").asText();
            BigDecimal price = new BigDecimal(jsonNode.get("price").asText());
            long tradeTime = jsonNode.get("tradeTime").asLong(); // 獲取 tradeTime

            Instant tradeInstant = Instant.ofEpochSecond(tradeTime);

            // 使用 symbol, price 和 tradeTime 推送到 WebSocket
            klineWebSocketHandler.broadcastKlineUpdate(symbol, price, tradeInstant);
        } catch (Exception e) {
            // 處理異常
            e.printStackTrace();
        }
    }
}
