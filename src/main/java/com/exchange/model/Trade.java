package com.exchange.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trades", indexes = {
        @Index(name = "idx_buy_order_id", columnList = "buy_order_id"),
        @Index(name = "idx_sell_order_id", columnList = "sell_order_id"),
        @Index(name = "idx_trade_time", columnList = "trade_time")
})
@Data
@NoArgsConstructor
public class Trade {

    @Id
    @Column(length = 20, nullable = false)
    private String id;  // 雪花ID

    @ManyToOne
    @JoinColumn(name = "buy_order_id", referencedColumnName = "id", nullable = false)
    private Order buyOrder;

    @ManyToOne
    @JoinColumn(name = "sell_order_id", referencedColumnName = "id", nullable = false)
    private Order sellOrder;

    @Column(length = 20, nullable = false)
    private String symbol;

    @Column(precision = 18, scale = 8, nullable = false)
    private BigDecimal price;

    @Column(precision = 18, scale = 8, nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private Instant tradeTime = Instant.now();
}