package com.exchange.producer;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WebSocketNotification {
    private String userId;
    private String eventType;
    private Object data;
}