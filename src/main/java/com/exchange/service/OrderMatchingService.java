package com.exchange.service;

import com.exchange.model.Order;
import com.exchange.model.Trade;
import com.exchange.producer.MatchedOrderProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Service
public class OrderMatchingService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private MatchedOrderProducer matchedOrderProducer;

    public void processOrder(Order order) {
        if (order.getOrderType() == Order.OrderType.MARKET) {
            matchMarketOrder(order);
        } else if (order.getOrderType() == Order.OrderType.LIMIT) {
            matchLimitOrder(order);
        }
    }

    // 市價單撮合邏輯
    private void matchMarketOrder(Order order) {
        matchOrder(order, true);
    }

    // 限價單撮合邏輯
    private void matchLimitOrder(Order order) {
        saveOrderToRedis(order);
        matchOrder(order, false);
    }

    // 提取撮合邏輯
    private void matchOrder(Order order, boolean isMarketOrder) {
        String orderbookKey = "orderbook:" + order.getSymbol() + ":" + order.getSide();
        String oppositeOrderbookKey = "orderbook:" + order.getSymbol() + ":" +
                (order.getSide() == Order.Side.BUY ? "SELL" : "BUY");

        while (order.getQuantity().subtract(order.getFilledQuantity()).compareTo(BigDecimal.ZERO) > 0) {
            Set<ZSetOperations.TypedTuple<String>> oppositeOrders = redisTemplate.opsForZSet()
                    .rangeWithScores(oppositeOrderbookKey, 0, 0);

            // 如果沒有對手訂單，對於限價單將訂單放回訂單簿並退出
            if (oppositeOrders == null || oppositeOrders.isEmpty()) {
                if (!isMarketOrder) {
                    redisTemplate.opsForZSet().add(orderbookKey, order.getId(), order.getPrice().doubleValue());
                }
                break;
            }

            ZSetOperations.TypedTuple<String> oppositeOrderTuple = oppositeOrders.iterator().next();
            String oppositeOrderId = oppositeOrderTuple.getValue();
            Order oppositeOrder = getOrderFromRedis(oppositeOrderId);
            BigDecimal oppositeOrderPrice = new BigDecimal(oppositeOrderTuple.getScore());

            // 對於限價單，檢查價格是否符合條件
            if (!isMarketOrder) {
                if ((order.getSide() == Order.Side.BUY && order.getPrice().compareTo(oppositeOrderPrice) < 0) ||
                        (order.getSide() == Order.Side.SELL && order.getPrice().compareTo(oppositeOrderPrice) > 0)) {
                    // 如果價格不匹配，將限價單保留在訂單簿中
                    redisTemplate.opsForZSet().add(orderbookKey, order.getId(), order.getPrice().doubleValue());
                    break;
                }
            }

            BigDecimal availableQuantity = order.getQuantity().subtract(order.getFilledQuantity());
            BigDecimal oppositeAvailableQuantity = oppositeOrder.getQuantity().subtract(oppositeOrder.getFilledQuantity());
            BigDecimal tradeQuantity = availableQuantity.min(oppositeAvailableQuantity);

            // 更新成交量
            updateTrade(order, oppositeOrder, tradeQuantity);

            // 保存交易數據
            saveTrade(order, oppositeOrder, tradeQuantity, oppositeOrderPrice);

            // 更新對手訂單
            updateOrderInRedis(oppositeOrder, oppositeOrderbookKey);
            updateOrderInRedis(order, orderbookKey);
        }
    }

    // 更新成交量並處理狀態
    private void updateTrade(Order order, Order oppositeOrder, BigDecimal tradeQuantity) {
        order.setFilledQuantity(order.getFilledQuantity().add(tradeQuantity));
        oppositeOrder.setFilledQuantity(oppositeOrder.getFilledQuantity().add(tradeQuantity));

        LocalDateTime currentTime = LocalDateTime.now(); // 取得當前時間

        if (oppositeOrder.getFilledQuantity().compareTo(oppositeOrder.getQuantity()) >= 0) {
            oppositeOrder.setStatus(Order.OrderStatus.COMPLETED);
            oppositeOrder.setUpdatedAt(currentTime); // 更新完成時間
            redisTemplate.delete("order:" + oppositeOrder.getId());
        } else {
            oppositeOrder.setStatus(Order.OrderStatus.PARTIALLY_FILLED);
            oppositeOrder.setUpdatedAt(currentTime); // 更新部分成交時間
        }

        if (order.getFilledQuantity().compareTo(order.getQuantity()) >= 0) {
            order.setStatus(Order.OrderStatus.COMPLETED);
            order.setUpdatedAt(currentTime); // 更新完成時間
            redisTemplate.delete("order:" + order.getId());
        } else {
            order.setStatus(Order.OrderStatus.PARTIALLY_FILLED);
            order.setUpdatedAt(currentTime); // 更新部分成交時間
        }

        // 發送匹配成功的訂單和交易到 Kafka
        matchedOrderProducer.sendMatchedOrder(order);
        matchedOrderProducer.sendMatchedOrder(oppositeOrder);
    }

    // 更新訂單狀態到 Redis 和 ZSet
    private void updateOrderInRedis(Order order, String orderbookKey) {
        if (order.getStatus() != Order.OrderStatus.COMPLETED) {
            saveOrderToRedis(order);
        }
        redisTemplate.opsForZSet().remove(orderbookKey, order.getId());
        if (order.getStatus() == Order.OrderStatus.PARTIALLY_FILLED) {
            redisTemplate.opsForZSet().add(orderbookKey, order.getId(), order.getPrice().doubleValue());
        }
    }

    // 保存訂單到 Redis
    private void saveOrderToRedis(Order order) {
        String orderKey = "order:" + order.getId();
        redisTemplate.opsForHash().put(orderKey, "id", order.getId());
        redisTemplate.opsForHash().put(orderKey, "userId", order.getUserId());
        redisTemplate.opsForHash().put(orderKey, "symbol", order.getSymbol());
        redisTemplate.opsForHash().put(orderKey, "price", order.getPrice().toString());
        redisTemplate.opsForHash().put(orderKey, "quantity", order.getQuantity().toString());
        redisTemplate.opsForHash().put(orderKey, "filledQuantity", order.getFilledQuantity().toString());
        redisTemplate.opsForHash().put(orderKey, "side", order.getSide().toString());
        redisTemplate.opsForHash().put(orderKey, "orderType", order.getOrderType().toString());
        redisTemplate.opsForHash().put(orderKey, "status", order.getStatus().toString());
        redisTemplate.opsForHash().put(orderKey, "createdAt", order.getCreatedAt().toString());
        redisTemplate.opsForHash().put(orderKey, "updatedAt", order.getUpdatedAt().toString());
    }

    // 保存交易數據到 Kafka
    private void saveTrade(Order buyOrder, Order sellOrder, BigDecimal tradeQuantity, BigDecimal price) {
        Trade trade = new Trade();
        trade.setId(generateTradeId()); // 使用雪花算法生成ID
        trade.setBuyOrder(buyOrder.getSide() == Order.Side.BUY ? buyOrder : sellOrder);
        trade.setSellOrder(buyOrder.getSide() == Order.Side.SELL ? buyOrder : sellOrder);
        trade.setQuantity(tradeQuantity);
        trade.setPrice(price);
        trade.setTradeTime(LocalDateTime.now());

        matchedOrderProducer.sendMatchedTrade(trade);
    }

    // 生成交易ID (可以使用你的雪花ID算法)
    private String generateTradeId() {
        // 這裡應實現生成唯一ID的邏輯 (例如雪花算法)
        return "some_unique_id";
    }

    // 從 Redis 獲取訂單
    public Order getOrderFromRedis(String orderId) {
        String orderKey = "order:" + orderId;
        String priceStr = (String) redisTemplate.opsForHash().get(orderKey, "price");
        String userIdStr = (String) redisTemplate.opsForHash().get(orderKey, "userId");
        String quantityStr = (String) redisTemplate.opsForHash().get(orderKey, "quantity");
        String quantityFilledStr = (String) redisTemplate.opsForHash().get(orderKey, "filledQuantity");
        String orderTypeStr = (String) redisTemplate.opsForHash().get(orderKey, "orderType");
        String sideStr = (String) redisTemplate.opsForHash().get(orderKey, "side");
        String symbolStr = (String) redisTemplate.opsForHash().get(orderKey, "symbol");
        String statusStr = (String) redisTemplate.opsForHash().get(orderKey, "status");
        String createdAtStr = (String) redisTemplate.opsForHash().get(orderKey, "createdAt");
        String updatedAtStr = (String) redisTemplate.opsForHash().get(orderKey, "updatedAt");

        if (priceStr == null || quantityStr == null || orderTypeStr == null || sideStr == null ||
                symbolStr == null || userIdStr == null || statusStr == null || createdAtStr == null || updatedAtStr == null) {
            throw new IllegalStateException("Redis 中的訂單數據缺失: " + orderId);
        }

        Order order = new Order();
        order.setId(orderId);
        order.setUserId(userIdStr);
        order.setPrice(new BigDecimal(priceStr));
        order.setQuantity(new BigDecimal(quantityStr));
        order.setFilledQuantity(new BigDecimal(quantityFilledStr));
        order.setOrderType(Order.OrderType.valueOf(orderTypeStr));
        order.setSide(Order.Side.valueOf(sideStr));
        order.setSymbol(symbolStr);
        order.setStatus(Order.OrderStatus.valueOf(statusStr));
        order.setCreatedAt(LocalDateTime.parse(createdAtStr));
        order.setUpdatedAt(LocalDateTime.parse(updatedAtStr));
        return order;
    }
}
