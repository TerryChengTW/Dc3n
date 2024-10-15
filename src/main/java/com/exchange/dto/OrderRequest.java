package com.exchange.dto;

import com.exchange.model.Order.Side;
import com.exchange.model.Order.OrderType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
public class OrderRequest {
    private String userId;
    private String symbol;
    private BigDecimal oldPrice; // 用於查詢的舊價格
    private BigDecimal price; // 用於更新的新價格
    private BigDecimal quantity;
    private Side side;
    private OrderType orderType;
    private ZonedDateTime modifiedAt; // 最後修改時間
}
