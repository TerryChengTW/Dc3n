package com.exchange.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @OneToMany(mappedBy = "buyOrder")
    private List<Trade> buyTrades;

    @OneToMany(mappedBy = "sellOrder")
    private List<Trade> sellTrades;

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
