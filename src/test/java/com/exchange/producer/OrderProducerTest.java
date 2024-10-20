package com.exchange.producer;

import com.exchange.model.Order;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

public class OrderProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderProducer orderProducer;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testSendNewOrder_Success() throws JsonProcessingException {
        // 準備訂單和JSON字符串
        Order order = new Order();
        order.setId("123");
        String orderJson = "{\"id\": \"123\"}";

        // 模擬 ObjectMapper 的行為
        when(objectMapper.writeValueAsString(order)).thenReturn(orderJson);

        // 模擬 KafkaTemplate 的成功發送行為
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send("new_orders", orderJson)).thenReturn(future);

        // 執行發送
        orderProducer.sendNewOrder(order);

        // 驗證是否調用了 KafkaTemplate 的 send 方法
        verify(kafkaTemplate, times(1)).send("new_orders", orderJson);
    }

    @Test
    public void testSendNewOrder_Failure() throws JsonProcessingException {
        // 準備訂單和JSON字符串
        Order order = new Order();
        order.setId("123");
        String orderJson = "{\"id\": \"123\"}";

        // 模擬 ObjectMapper 的行為
        when(objectMapper.writeValueAsString(order)).thenReturn(orderJson);

        // 模擬 KafkaTemplate 發送失敗的情況
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka send failed"));
        when(kafkaTemplate.send("new_orders", orderJson)).thenReturn(future);

        // 執行發送
        orderProducer.sendNewOrder(order);

        // 驗證是否調用了 KafkaTemplate 的 send 方法
        verify(kafkaTemplate, times(1)).send("new_orders", orderJson);
    }

    @Test
    public void testSendNewOrder_JsonProcessingException() throws JsonProcessingException {
        // 準備訂單
        Order order = new Order();

        // 模擬 ObjectMapper 拋出異常
        when(objectMapper.writeValueAsString(order)).thenThrow(new JsonProcessingException("Error") {});

        // 執行發送
        orderProducer.sendNewOrder(order);

        // 確認 KafkaTemplate 的 send 方法沒有被調用，因為 JSON 轉換失敗
        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }
}
