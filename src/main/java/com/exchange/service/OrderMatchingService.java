package com.exchange.service;

import com.exchange.model.Order;
import com.exchange.model.Trade;
import com.exchange.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class OrderMatchingService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void processOrder(Order order) {
        if (order.getOrderType() == Order.OrderType.MARKET) {
            matchMarketOrder(order);
        } else if (order.getOrderType() == Order.OrderType.LIMIT) {
            matchLimitOrder(order);
        }
    }

    private void matchMarketOrder(Order order) {
        // TODO: 市價單撮合邏輯修正
        String oppositeOrderbookKey = "orderbook:" + order.getSymbol() + ":" +
                (order.getSide() == Order.Side.BUY ? "SELL" : "BUY");

        while (order.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            // 不移除訂單，只是查詢對手方的最低價訂單
            Set<ZSetOperations.TypedTuple<String>> oppositeOrders = redisTemplate.opsForZSet()
                    .rangeWithScores(oppositeOrderbookKey, 0, 0);

            if (oppositeOrders == null || oppositeOrders.isEmpty()) {
                // 沒有匹配的訂單，將剩餘數量轉為限價單
                order.setOrderType(Order.OrderType.LIMIT);
                matchLimitOrder(order);
                break;
            }

            ZSetOperations.TypedTuple<String> oppositeOrderTuple = oppositeOrders.iterator().next();
            String oppositeOrderId = oppositeOrderTuple.getValue();
            BigDecimal oppositeOrderPrice = BigDecimal.valueOf(oppositeOrderTuple.getScore());

            // 價格匹配，即市價單可以立即交易
            Order oppositeOrder = getOrderFromRedis(oppositeOrderId);
            BigDecimal tradeQuantity = order.getQuantity().min(oppositeOrder.getQuantity());
            BigDecimal tradePrice = oppositeOrderPrice;

            // 移除匹配的訂單
            redisTemplate.opsForZSet().remove(oppositeOrderbookKey, oppositeOrderId);
            executeTrade(order, oppositeOrder, tradeQuantity, tradePrice);

            order.setQuantity(order.getQuantity().subtract(tradeQuantity));
            oppositeOrder.setQuantity(oppositeOrder.getQuantity().subtract(tradeQuantity));

            if (oppositeOrder.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                // 如果對手訂單還有剩餘，將其放回訂單簿
                redisTemplate.opsForZSet().add(oppositeOrderbookKey, oppositeOrder.getId(),
                        oppositeOrder.getPrice().doubleValue());
            }
        }
    }


    private void matchLimitOrder(Order order) {
        String orderbookKey = "orderbook:" + order.getSymbol() + ":" + order.getSide();
        String oppositeOrderbookKey = "orderbook:" + order.getSymbol() + ":" +
                (order.getSide() == Order.Side.BUY ? "SELL" : "BUY");

        // 將完整的訂單信息存入 Redis，鍵為 "order:{orderId}"
        saveOrderToRedis(order);

        while (order.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            // 不移除訂單，只是查詢對手方的最低價訂單
            Set<ZSetOperations.TypedTuple<String>> oppositeOrders = redisTemplate.opsForZSet()
                    .rangeWithScores(oppositeOrderbookKey, 0, 0);

            if (oppositeOrders == null || oppositeOrders.isEmpty()) {
                // 沒有匹配的訂單，將訂單放入訂單簿
                redisTemplate.opsForZSet().add(orderbookKey, order.getId(), order.getPrice().doubleValue());
                break;
            }

            ZSetOperations.TypedTuple<String> oppositeOrderTuple = oppositeOrders.iterator().next();
            String oppositeOrderId = oppositeOrderTuple.getValue();
            BigDecimal oppositeOrderPrice = BigDecimal.valueOf(oppositeOrderTuple.getScore());

            // 比較價格是否能夠匹配
            if ((order.getSide() == Order.Side.BUY && order.getPrice().compareTo(oppositeOrderPrice) < 0) ||
                    (order.getSide() == Order.Side.SELL && order.getPrice().compareTo(oppositeOrderPrice) > 0)) {
                // 價格不匹配，將訂單放入訂單簿
                redisTemplate.opsForZSet().add(orderbookKey, order.getId(), order.getPrice().doubleValue());
                break;
            }

            // 價格匹配，進行交易
            Order oppositeOrder = getOrderFromRedis(oppositeOrderId); // 從 Redis 中獲取完整的對手訂單
            BigDecimal tradeQuantity = order.getQuantity().min(oppositeOrder.getQuantity()); // 使用對手方訂單的數量來計算交易量
            BigDecimal tradePrice = oppositeOrderPrice;

            // 移除匹配的對手方訂單
            redisTemplate.opsForZSet().remove(oppositeOrderbookKey, oppositeOrderId);

            // 更新訂單數量與狀態
            order.setQuantity(order.getQuantity().subtract(tradeQuantity));
            oppositeOrder.setQuantity(oppositeOrder.getQuantity().subtract(tradeQuantity));

            // 如果對手方訂單還有剩餘，重新放回訂單簿
            if (oppositeOrder.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                redisTemplate.opsForZSet().add(oppositeOrderbookKey, oppositeOrder.getId(), oppositeOrder.getPrice().doubleValue());
            }

            // 執行交易，這裡傳遞完整的對手方訂單
            executeTrade(order, oppositeOrder, tradeQuantity, tradePrice);

            // 更新訂單狀態
            updateOrderStatus(order);
            updateOrderStatus(oppositeOrder);
        }

        if (order.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            // 如果訂單還有剩餘，將其放入訂單簿
            redisTemplate.opsForZSet().add(orderbookKey, order.getId(), order.getPrice().doubleValue());
        }
    }

    private void saveOrderToRedis(Order order) {
        String orderKey = "order:" + order.getId();

        // 打印關鍵字段
        System.out.println("存入 Redis 的價格: " + order.getPrice());
        System.out.println("存入 Redis 的數量: " + order.getQuantity());

        redisTemplate.opsForHash().put(orderKey, "id", order.getId());
        redisTemplate.opsForHash().put(orderKey, "symbol", order.getSymbol());
        redisTemplate.opsForHash().put(orderKey, "price", order.getPrice().toString());
        redisTemplate.opsForHash().put(orderKey, "quantity", order.getQuantity().toString());
        redisTemplate.opsForHash().put(orderKey, "side", order.getSide().toString());
        redisTemplate.opsForHash().put(orderKey, "orderType", order.getOrderType().toString());
        redisTemplate.opsForHash().put(orderKey, "createdAt", order.getCreatedAt().toString());
    }


    private void executeTrade(Order order1, Order oppositeOrder, BigDecimal quantity, BigDecimal price) {
        Trade trade = new Trade();
        trade.setBuyOrder(order1.getSide() == Order.Side.BUY ? order1 : oppositeOrder);
        trade.setSellOrder(order1.getSide() == Order.Side.SELL ? order1 : oppositeOrder);
        trade.setPrice(price);
        trade.setQuantity(quantity);
        trade.setTradeTime(LocalDateTime.now());

        // 保存交易到數據庫
        saveTrade(trade);

        // 更新用戶餘額
        updateUserBalance(order1.getUser(), oppositeOrder.getUser(), trade);
    }


    private Order getOrderFromRedis(String orderId) {
        String orderKey = "order:" + orderId;

        String priceStr = (String) redisTemplate.opsForHash().get(orderKey, "price");
        String quantityStr = (String) redisTemplate.opsForHash().get(orderKey, "quantity");

        // 打印從 Redis 讀取的價格和數量
        System.out.println("從 Redis 中讀取的價格: " + priceStr);
        System.out.println("從 Redis 中讀取的數量: " + quantityStr);

        if (priceStr == null || quantityStr == null) {
            throw new IllegalStateException("Redis 中的訂單數據缺失: " + orderId);
        }

        Order order = new Order();
        order.setId((String) redisTemplate.opsForHash().get(orderKey, "id"));
        order.setSymbol((String) redisTemplate.opsForHash().get(orderKey, "symbol"));
        order.setPrice(new BigDecimal(priceStr)); // 確保 priceStr 不為 null
        order.setQuantity(new BigDecimal(quantityStr)); // 確保 quantityStr 不為 null
        order.setSide(Order.Side.valueOf((String) redisTemplate.opsForHash().get(orderKey, "side")));
        order.setOrderType(Order.OrderType.valueOf((String) redisTemplate.opsForHash().get(orderKey, "orderType")));
        order.setCreatedAt(LocalDateTime.parse((String) redisTemplate.opsForHash().get(orderKey, "createdAt")));

        return order;
    }


    private BigDecimal getOrderPrice(String orderId, String symbol, Order.Side side) {
        // 根據符號和買賣方向組成 orderbookKey
        String orderbookKey = "orderbook:" + symbol + ":" + (side == Order.Side.BUY ? "BUY" : "SELL");

        // 從 Redis 的有序集合中獲取訂單的價格 (score)
        Double score = redisTemplate.opsForZSet().score(orderbookKey, orderId);

        if (score != null) {
            return BigDecimal.valueOf(score);
        }

        return null; // 如果找不到價格，返回 null
    }

    private void saveTrade(Trade trade) {
        // 保存交易到數據庫
        // 這裡需要實現保存交易到數據庫的邏輯
    }

    private void updateOrderStatus(Order order) {
        // 更新訂單狀態
        // 這裡需要實現更新訂單狀態的邏輯
    }

    private void updateUserBalance(User buyer, User seller, Trade trade) {
        // 更新用戶餘額
        // 這裡需要實現更新用戶餘額的邏輯
    }
}