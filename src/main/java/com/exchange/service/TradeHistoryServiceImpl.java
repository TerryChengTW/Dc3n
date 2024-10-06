package com.exchange.service;

import com.exchange.dto.TradeHistoryRequest;
import com.exchange.dto.TradeHistoryResponse;
import com.exchange.model.Order;
import com.exchange.model.Trade;
import com.exchange.repository.OrderRepository;
import com.exchange.repository.TradeRepository;
import com.exchange.service.TradeHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TradeHistoryServiceImpl implements TradeHistoryService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Override
    public List<TradeHistoryResponse> getTradeHistory(String userId, TradeHistoryRequest request) {
        Instant endTime = Instant.now();
        Instant startTime = getStartTime(endTime, request.getTimeRange());

        List<Order> orders = orderRepository.findByUserIdAndUpdatedAtBetween(userId, startTime, endTime);

        return orders.stream()
                .flatMap(order -> tradeRepository.findByBuyOrderIdOrSellOrderId(order.getId(), order.getId()).stream()
                        .filter(trade -> shouldIncludeTrade(trade, order, request.getDirection()))
                        .map(trade -> createTradeHistoryResponse(trade, order)))
                .collect(Collectors.toList());
    }

    private Instant getStartTime(Instant endTime, TradeHistoryRequest.TimeRange timeRange) {
        switch (timeRange) {
            case ONE_DAY:
                return endTime.minus(1, ChronoUnit.DAYS);
            case THREE_DAYS:
                return endTime.minus(3, ChronoUnit.DAYS);
            case SEVEN_DAYS:
                return endTime.minus(7, ChronoUnit.DAYS);
            default:
                throw new IllegalArgumentException("Invalid time range");
        }
    }

    private boolean shouldIncludeTrade(Trade trade, Order order, TradeHistoryRequest.TradeDirection direction) {
        if (direction == TradeHistoryRequest.TradeDirection.ALL) {
            return true;
        }
        boolean isBuy = trade.getBuyOrder().getId().equals(order.getId());
        return (direction == TradeHistoryRequest.TradeDirection.BUY && isBuy) ||
                (direction == TradeHistoryRequest.TradeDirection.SELL && !isBuy);
    }


    private TradeHistoryResponse createTradeHistoryResponse(Trade trade, Order order) {
        TradeHistoryResponse response = new TradeHistoryResponse();
        response.setTradeId(trade.getId());  // 使用 trade ID
        response.setTradeTime(trade.getTradeTime());
        response.setSymbol(trade.getSymbol());

        boolean isBuy = trade.getBuyOrder().getId().equals(order.getId());
        response.setDirection(isBuy ? "BUY" : "SELL");

        response.setAvgPrice(trade.getPrice());
        response.setQuantity(trade.getQuantity());

        boolean isTaker = trade.getTakerOrderId().equals(order.getId());
        response.setRole(isTaker ? "TAKER" : "MAKER");

        response.setTotalAmount(trade.getPrice().multiply(trade.getQuantity()));

        return response;
    }
}