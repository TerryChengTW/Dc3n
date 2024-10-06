package com.exchange.service;

import com.exchange.dto.TradeHistoryRequest;
import com.exchange.dto.TradeHistoryResponse;
import com.exchange.model.Order;
import com.exchange.model.Trade;
import com.exchange.repository.OrderRepository;
import com.exchange.repository.TradeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
        // 使用 request 中的 startTime 和 endTime
        List<Order> orders = orderRepository.findByUserIdAndUpdatedAtBetween(userId, request.getStartTime(), request.getEndTime());

        return orders.stream()
                .flatMap(order -> tradeRepository.findByBuyOrderIdOrSellOrderId(order.getId(), order.getId()).stream()
                        .filter(trade -> shouldIncludeTrade(trade, order, request.getDirection()))
                        .map(trade -> createTradeHistoryResponse(trade, order)))
                .collect(Collectors.toList());
    }

    // 根據方向篩選 trade
    private boolean shouldIncludeTrade(Trade trade, Order order, TradeHistoryRequest.TradeDirection direction) {
        if (direction == TradeHistoryRequest.TradeDirection.ALL) {
            return true;
        }
        boolean isBuy = trade.getBuyOrder().getId().equals(order.getId());
        return (direction == TradeHistoryRequest.TradeDirection.BUY && isBuy) ||
                (direction == TradeHistoryRequest.TradeDirection.SELL && !isBuy);
    }

    // 創建 TradeHistoryResponse
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
