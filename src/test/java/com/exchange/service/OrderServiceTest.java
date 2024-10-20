package com.exchange.service;

import com.exchange.model.Order;
import com.exchange.producer.OrderProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class OrderServiceTest {

    @Mock
    private OrderProducer orderProducer;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    public void setup() {
        // 初始化 Mockito 設置
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testSaveOrder_Success() {
        // 準備一個限價單
        Order limitOrder = new Order();
        limitOrder.setOrderType(Order.OrderType.LIMIT);
        limitOrder.setPrice(new BigDecimal("50000"));

        // 調用 orderService 的 saveOrder 方法
        orderService.saveOrder(limitOrder);

        // 驗證是否調用了 orderProducer 的 sendNewOrder 方法
        verify(orderProducer, times(1)).sendNewOrder(limitOrder);
    }

    @Test
    public void testSaveOrder_MarketOrderShouldNotHavePrice() {
        // 準備一個市價單，但設置了價格
        Order marketOrder = new Order();
        marketOrder.setOrderType(Order.OrderType.MARKET);
        marketOrder.setPrice(new BigDecimal("50000"));

        // 測試是否拋出了 IllegalArgumentException
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.saveOrder(marketOrder);
        });

        assertEquals("市價單不應該設置價格", exception.getMessage());

        // 驗證是否沒有調用 orderProducer 的 sendNewOrder 方法
        verify(orderProducer, never()).sendNewOrder(marketOrder);
    }

    @Test
    public void testSaveOrder_LimitOrderShouldHavePrice() {
        // 準備一個限價單，但沒有設置價格
        Order limitOrder = new Order();
        limitOrder.setOrderType(Order.OrderType.LIMIT);

        // 測試是否拋出了 IllegalArgumentException
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.saveOrder(limitOrder);
        });

        assertEquals("限價單需要設置價格", exception.getMessage());

        // 驗證是否沒有調用 orderProducer 的 sendNewOrder 方法
        verify(orderProducer, never()).sendNewOrder(limitOrder);
    }
}
