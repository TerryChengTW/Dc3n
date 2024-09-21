package com.exchange.controller;

import com.exchange.model.MarketData;
import com.exchange.repository.MarketDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class KlineController {

    @Autowired
    private MarketDataRepository marketDataRepository;

    @GetMapping("/api/kline/{symbol}/{timestamp}")
    public List<Map<String, Object>> getKlineData(
            @PathVariable String symbol,
            @PathVariable long timestamp,
            @RequestParam(defaultValue = "1m") String timeframe) {

        // 將秒級時間戳轉換為毫秒級
        Instant time = Instant.ofEpochMilli(timestamp * 1000);

        List<MarketData> data;

        // 根據 timeframe 調用不同的查詢方法
        switch (timeframe) {
            case "5m":
                data = marketDataRepository.findTop500BySymbolAnd5mTimeFrameBeforeOrderByTimestampDesc(symbol, time);
                break;
            case "1h":
                data = marketDataRepository.findTop500BySymbolAnd1hTimeFrameBeforeOrderByTimestampDesc(symbol, time);
                break;
            default:
                data = marketDataRepository.findTop500BySymbolAnd1mTimeFrameBeforeOrderByTimestampDesc(symbol, time);
        }

        // 將 MarketData 轉換為符合前端格式的 JSON 格式
        List<Map<String, Object>> resultData = new ArrayList<>();
        for (MarketData marketData : data) {
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("time", marketData.getTimestamp().getEpochSecond());  // 使用秒級 Unix 時間戳
            dataMap.put("open", marketData.getOpen());
            dataMap.put("high", marketData.getHigh());
            dataMap.put("low", marketData.getLow());
            dataMap.put("close", marketData.getClose());
            resultData.add(dataMap);
        }

        return resultData;
    }

}
