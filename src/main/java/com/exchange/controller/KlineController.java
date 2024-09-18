package com.exchange.controller;

import com.exchange.model.MarketData;
import com.exchange.repository.MarketDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class KlineController {

    @Autowired
    private MarketDataRepository marketDataRepository;

    @GetMapping("/api/kline/{symbol}")
    public List<MarketData> getKlineData(@PathVariable String symbol) {
        return marketDataRepository.findTop500BySymbolOrderByTimestampDesc(symbol);
    }
}
