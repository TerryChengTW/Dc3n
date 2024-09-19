package com.exchange.model;

import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.Instant;

@EqualsAndHashCode
public class MarketDataId implements Serializable {
    private String symbol;
    private String timeFrame;
    private Instant timestamp;
}
