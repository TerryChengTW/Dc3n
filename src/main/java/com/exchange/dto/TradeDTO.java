package com.exchange.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Setter
@Getter
@AllArgsConstructor
public class TradeDTO {
    private String symbol;
    private Instant tradeTime;
    private BigDecimal price;
    private BigDecimal quantity;

}
