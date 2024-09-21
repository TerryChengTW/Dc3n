package com.exchange.utils;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class OrderProcessingData {
    private String orderId;
    private long redisFetchTime;
    private long tradeUpdateTime;
    private long redisUpdateTime;
    private long totalProcessingTime;
    private long bigDecimalOperationTime;
    private long objectCreationTime;
    private long untrackedTime;

    // Getters and Setters

}
