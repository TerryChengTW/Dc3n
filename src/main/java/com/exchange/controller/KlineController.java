package com.exchange.controller;

import com.exchange.model.MarketData;
import com.exchange.repository.MarketDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
            @PathVariable long timestamp) {

        // 將秒級時間戳轉換為毫秒級
        System.out.println("Timestamp: " + timestamp);
        Instant time = Instant.ofEpochMilli(timestamp * 1000);

        List<MarketData> data = marketDataRepository.findTop500BySymbolAndTimestampBeforeOrderByTimestampDesc(symbol, time);


        // 將 MarketData 轉換為符合前端格式的簡化 JSON 格式
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

        System.out.println("Result data: ");
        System.out.println(resultData);
        return resultData;
    }

}
