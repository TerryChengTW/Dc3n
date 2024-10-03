package com.exchange.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Getter
@Setter
@AllArgsConstructor
public class OrderDTO {
    private String id;
    private String userId;
    private String symbol;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal filledQuantity;
    private BigDecimal unfilledQuantity;
    private String side;
    private String orderType;
    private String status;
    private BigDecimal stopPrice;
    private BigDecimal takeProfitPrice;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime modifiedAt;

}