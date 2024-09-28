package com.exchange.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
public class OrderSummary {
    private String orderId;
    private String symbol;
    private BigDecimal price;
    private BigDecimal unfilledQuantity;
    private Order.Side side;
    private Instant modifiedAt;
    private String zsetValue; // 新增 zsetValue 字段
}
