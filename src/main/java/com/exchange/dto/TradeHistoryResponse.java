package com.exchange.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
public class TradeHistoryResponse {
    private String tradeId;
    private Instant tradeTime;
    private String symbol;
    private String direction;
    private BigDecimal avgPrice;
    private BigDecimal quantity;
    private String role;
    private BigDecimal totalAmount;
}