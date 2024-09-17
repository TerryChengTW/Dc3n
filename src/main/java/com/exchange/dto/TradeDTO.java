package com.exchange.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Setter
@Getter
@AllArgsConstructor
public class TradeDTO {
    private String symbol;
    private LocalDateTime tradeTime;
    private BigDecimal price;
    private BigDecimal quantity;

}
