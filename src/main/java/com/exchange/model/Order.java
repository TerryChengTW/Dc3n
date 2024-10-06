package com.exchange.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_symbol", columnList = "symbol")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @Column(length = 20, nullable = false)
    private String id;  // 雪花ID

    @Column(name = "user_id", length = 20, nullable = false)
    private String userId;

    @Column(length = 10, nullable = false)
    private String symbol;   // 交易對，如 BTC/USDT

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Column(precision = 18, scale = 8)
    private BigDecimal price;

    @Column(precision = 18, scale = 8, nullable = false)
    private BigDecimal quantity;  // 原始下單數量

    @Column(precision = 18, scale = 8, nullable = false)
    private BigDecimal filledQuantity = BigDecimal.ZERO;

    @Column(precision = 18, scale = 8, nullable = false)
    private BigDecimal unfilledQuantity;

    @Enumerated(EnumType.STRING)
    @Column(length = 4, nullable = false)
    private Side side;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private OrderType orderType; // "LIMIT" 或 "MARKET" 或其他類型

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private OrderStatus status = OrderStatus.PENDING;  // 默認狀態

    @Column(precision = 18, scale = 8)
    private BigDecimal stopPrice;  // 止損價格

    @Column(precision = 18, scale = 8)
    private BigDecimal takeProfitPrice;  // 止盈價格

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(nullable = false)
    private Instant modifiedAt = Instant.now();

    @OneToMany(mappedBy = "buyOrder")
    @JsonIgnore
    private List<Trade> buyTrades;

    @OneToMany(mappedBy = "sellOrder")
    @JsonIgnore
    private List<Trade> sellTrades;

    public Order(String id, String userId, String symbol, BigDecimal price, BigDecimal quantity, BigDecimal filledQuantity, BigDecimal unfilledQuantity, Side side, OrderType orderType, OrderStatus status, BigDecimal stopPrice, BigDecimal takeProfitPrice, Instant createdAt, Instant updatedAt, Instant modifiedAt) {
        this.id = id;
        this.userId = userId;
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.filledQuantity = filledQuantity;
        this.unfilledQuantity = unfilledQuantity;
        this.side = side;
        this.orderType = orderType;
        this.status = status;
        this.stopPrice = stopPrice;
        this.takeProfitPrice = takeProfitPrice;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.modifiedAt = modifiedAt;
    }

    public enum OrderType {
        LIMIT,
        MARKET,
        TAKE_PROFIT,
        STOP_LOSS
    }

    public enum OrderStatus {
        PENDING,
        PARTIALLY_FILLED,
        COMPLETED,
        CANCELLED
    }

    public enum Side {
        BUY,
        SELL
    }
}
