package com.exchange.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class MatchedMessage {
    // Getters and Setters
    private String type; // 消息類型，例如 "ORDER" 或 "TRADE"
    private String data; // 封裝的 JSON 數據

    // Constructors
    public MatchedMessage() {}

    public MatchedMessage(String type, String data) {
        this.type = type;
        this.data = data;
    }

}
