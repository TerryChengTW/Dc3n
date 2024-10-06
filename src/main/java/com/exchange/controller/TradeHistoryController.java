package com.exchange.controller;

import com.exchange.dto.TradeHistoryRequest;
import com.exchange.dto.TradeHistoryResponse;
import com.exchange.service.TradeHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/trade-history")
public class TradeHistoryController {

    @Autowired
    private TradeHistoryService tradeHistoryService;

    @GetMapping
    public ResponseEntity<List<TradeHistoryResponse>> getTradeHistory(
            @RequestParam(required = false, defaultValue = "1") String timeRange,
            @RequestParam(required = false, defaultValue = "ALL") TradeHistoryRequest.TradeDirection direction,
            HttpServletRequest httpRequest) {

        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED).build();
        }

        Instant endTime = Instant.now();
        Instant startTime = getStartTime(timeRange, endTime);

        TradeHistoryRequest request = new TradeHistoryRequest();
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setDirection(direction);

        List<TradeHistoryResponse> tradeHistory = tradeHistoryService.getTradeHistory(userId, request);
        return ResponseEntity.ok(tradeHistory);
    }

    // 解析時間範圍
    private Instant getStartTime(String timeRange, Instant endTime) {
        switch (timeRange) {
            case "7":
                return endTime.minus(7, ChronoUnit.DAYS);
            case "3":
                return endTime.minus(3, ChronoUnit.DAYS);
            default:
                return endTime.minus(1, ChronoUnit.DAYS);
        }
    }
}
