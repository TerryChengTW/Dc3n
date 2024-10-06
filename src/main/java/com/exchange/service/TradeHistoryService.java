package com.exchange.service;

import com.exchange.dto.TradeHistoryRequest;
import com.exchange.dto.TradeHistoryResponse;
import java.util.List;

public interface TradeHistoryService {
    List<TradeHistoryResponse> getTradeHistory(String userId, TradeHistoryRequest request);
}