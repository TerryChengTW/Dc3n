package com.exchange.dto;

import lombok.Data;

@Data
public class TradeHistoryRequest {
    private TimeRange timeRange;
    private TradeDirection direction;

    public enum TimeRange {
        ONE_DAY,
        THREE_DAYS,
        SEVEN_DAYS
    }

    public enum TradeDirection {
        BUY,
        SELL,
        ALL
    }
}