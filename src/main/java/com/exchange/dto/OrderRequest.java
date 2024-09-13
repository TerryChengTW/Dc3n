package com.exchange.dto;

import com.exchange.model.Order.OrderType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderRequest {
    private String userId;
    private String symbol;
    private BigDecimal price;
    private BigDecimal quantity;
    private OrderType orderType;
}
