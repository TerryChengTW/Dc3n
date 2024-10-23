package com.exchange.model;

import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "user_balances")
@IdClass(UserBalanceId.class)
@Data
@NoArgsConstructor
public class UserBalance {

    @Id
    private String userId;

    @Id
    @Column(length = 10, nullable = false)
    private String currency; // 如 BTC, USDT

    @Column(precision = 18, scale = 8, nullable = false)
    private BigDecimal availableBalance;

    @Column(precision = 18, scale = 8, nullable = false)
    private BigDecimal frozenBalance;
}
