package com.exchange.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;


@Getter
@Setter
@AllArgsConstructor
public class SimpleTradeInfo {
    private BigDecimal price;
    private BigDecimal quantity;
    private Instant tradeTime;
    private String role;

    // 構造函數、getter 和 setter
}