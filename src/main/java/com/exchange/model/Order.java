package com.exchange.model;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @Column(length = 20, nullable = false)
    private String orderId;  // 雪花ID

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    @Column(length = 10, nullable = false)
    private String symbol;   // 交易對，如 BTC/USDT

    @Column(precision = 18, scale = 8, nullable = false)
    private BigDecimal price;

    @Column(precision = 18, scale = 8, nullable = false)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)  // 使用 String 顯示枚舉值
    @Column(length = 20, nullable = false)
    private OrderType orderType; // "LIMIT" 或 "MARKET" 或其他類型

    @Enumerated(EnumType.STRING)  // 使用 String 顯示枚舉值
    @Column(length = 20, nullable = false)
    private OrderStatus status = OrderStatus.PENDING;  // 默認狀態

    @Column(precision = 18, scale = 8)
    private BigDecimal stopPrice;  // 止損價格

    @Column(precision = 18, scale = 8)
    private BigDecimal takeProfitPrice;  // 止盈價格

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum OrderType {
        LIMIT,
        MARKET,
        TAKE_PROFIT,
        STOP_LOSS
    }

    public enum OrderStatus {
        PENDING,
        COMPLETED,
        CANCELLED
    }
}
