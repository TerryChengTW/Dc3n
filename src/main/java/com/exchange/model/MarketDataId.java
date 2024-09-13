package com.exchange.model;

import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

@EqualsAndHashCode
public class MarketDataId implements Serializable {
    private String symbol;
    private String timeFrame;
    private LocalDateTime timestamp;

    // equals() 和 hashCode() 需要覆蓋
}
