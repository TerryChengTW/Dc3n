package com.exchange.controller;

import com.exchange.dto.TradeHistoryRequest;
import com.exchange.dto.TradeHistoryResponse;
import com.exchange.service.TradeHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/trade-history")
public class TradeHistoryController {

    @Autowired
    private TradeHistoryService tradeHistoryService;

    @GetMapping
    public ResponseEntity<List<TradeHistoryResponse>> getTradeHistory(
            @RequestParam(required = false, defaultValue = "ONE_DAY") TradeHistoryRequest.TimeRange timeRange,
            @RequestParam(required = false, defaultValue = "ALL") TradeHistoryRequest.TradeDirection direction,
            HttpServletRequest httpRequest) {

        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        TradeHistoryRequest request = new TradeHistoryRequest();
        request.setTimeRange(timeRange);
        request.setDirection(direction);

        List<TradeHistoryResponse> tradeHistory = tradeHistoryService.getTradeHistory(userId, request);
        return ResponseEntity.ok(tradeHistory);
    }
}