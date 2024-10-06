package com.exchange.dto;

import com.exchange.model.Order;
import com.exchange.model.Trade;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Setter
@AllArgsConstructor
public class OrderHistoryDTO {
    @JsonIgnore
    private Order order;

    @JsonIgnore
    private List<SimpleTradeInfo> trades;

    @JsonProperty("order")
    public Map<String, Object> getFormattedOrder() {
        Map<String, Object> formattedOrder = new HashMap<>();
        formattedOrder.put("id", order.getId());
        formattedOrder.put("symbol", order.getSymbol());
        formattedOrder.put("price", order.getPrice() != null ? order.getPrice().setScale(8, RoundingMode.HALF_UP).toPlainString() : null);
        formattedOrder.put("quantity", order.getQuantity().setScale(8, RoundingMode.HALF_UP).toPlainString());
        formattedOrder.put("filledQuantity", order.getFilledQuantity().setScale(8, RoundingMode.HALF_UP).toPlainString());
        formattedOrder.put("side", order.getSide().toString());
        formattedOrder.put("orderType", order.getOrderType().toString());
        formattedOrder.put("status", order.getStatus().toString());
        formattedOrder.put("createdAt", order.getCreatedAt().truncatedTo(ChronoUnit.SECONDS).toString());
        return formattedOrder;
    }

    @JsonProperty("trades")
    public List<Map<String, Object>> getFormattedTrades() {
        return trades.stream().map(trade -> {
            Map<String, Object> formattedTrade = new HashMap<>();
            formattedTrade.put("price", trade.getPrice().setScale(8, RoundingMode.HALF_UP).toPlainString());
            formattedTrade.put("quantity", trade.getQuantity().setScale(8, RoundingMode.HALF_UP).toPlainString());
            formattedTrade.put("tradeTime", trade.getTradeTime().truncatedTo(ChronoUnit.SECONDS).toString());
            formattedTrade.put("role", trade.getRole());
            return formattedTrade;
        }).collect(Collectors.toList());
    }
}