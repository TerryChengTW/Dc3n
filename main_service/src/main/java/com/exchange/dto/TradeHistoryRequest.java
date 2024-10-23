package com.exchange.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class TradeHistoryRequest {
    private Instant startTime;
    private Instant endTime;
    private TradeDirection direction;

    public enum TradeDirection {
        BUY,
        SELL,
        ALL
    }
}
