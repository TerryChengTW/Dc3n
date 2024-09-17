package com.exchange.service;

import com.exchange.model.Trade;
import com.exchange.repository.TradeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TradeService {

    @Autowired
    private TradeRepository tradeRepository;

    public List<Trade> getRecentTrades(String symbol, int limit) {
        return tradeRepository.findBySymbolOrderByTradeTimeDesc(symbol, PageRequest.of(0, limit));
    }
}