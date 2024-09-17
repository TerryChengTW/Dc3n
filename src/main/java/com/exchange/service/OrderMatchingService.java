package com.exchange.service;

import com.exchange.model.Order;
import com.exchange.model.Trade;
import com.exchange.producer.MatchedOrderProducer;
import com.exchange.producer.WebSocketNotificationProducer;
import com.exchange.utils.SnowflakeIdGenerator;
import com.exchange.websocket.OrderbookWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Service
public class OrderMatchingService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private MatchedOrderProducer matchedOrderProducer;

    @Autowired
    private WebSocketNotificationProducer webSocketNotificationProducer;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    private OrderbookWebSocketHandler orderbookWebSocketHandler;

    @Autowired
    private OrderbookService orderbookService;

    public void processOrder(Order order) {
        if (order.getOrderType() == Order.OrderType.MARKET) {
            matchMarketOrder(order);
        } else if (order.getOrderType() == Order.OrderType.LIMIT) {
            matchLimitOrder(order);
        }
    }

    private void matchMarketOrder(Order order) {
        matchOrder(order, true);
    }

    private void matchLimitOrder(Order order) {
        matchOrder(order, false);
        // 檢查是否有未匹配的數量，如果還有剩餘的數量，則將訂單放入 Redis
        if (order.getQuantity().subtract(order.getFilledQuantity()).compareTo(BigDecimal.ZERO) > 0) {
            saveOrderToRedis(order);
            addOrderToOrderbook(order, "orderbook:" + order.getSymbol() + ":" + order.getSide());
        }
    }

    private void matchOrder(Order order, boolean isMarketOrder) {
        String orderbookKey = "orderbook:" + order.getSymbol() + ":" + order.getSide();
        String oppositeOrderbookKey = "orderbook:" + order.getSymbol() + ":" +
                (order.getSide() == Order.Side.BUY ? "SELL" : "BUY");

        while (order.getQuantity().subtract(order.getFilledQuantity()).compareTo(BigDecimal.ZERO) > 0) {
            Set<ZSetOperations.TypedTuple<String>> oppositeOrders;
            if (order.getSide() == Order.Side.BUY) {
                // 買單應該匹配最低的賣價
                oppositeOrders = redisTemplate.opsForZSet().rangeWithScores(oppositeOrderbookKey, 0, 0);
            } else {
                // 賣單應該匹配最高的買價
                oppositeOrders = redisTemplate.opsForZSet().reverseRangeWithScores(oppositeOrderbookKey, 0, 0);
            }

            if (oppositeOrders == null || oppositeOrders.isEmpty()) {
                if (!isMarketOrder) {
                    addOrderToOrderbook(order, orderbookKey);
                }
                break;
            }

            ZSetOperations.TypedTuple<String> oppositeOrderTuple = oppositeOrders.iterator().next();
            String oppositeOrderId = oppositeOrderTuple.getValue();
            Order oppositeOrder = getOrderFromRedis(oppositeOrderId);
            BigDecimal oppositeOrderPrice = BigDecimal.valueOf(oppositeOrderTuple.getScore());

            if (!isMarketOrder) {
                if ((order.getSide() == Order.Side.BUY && order.getPrice().compareTo(oppositeOrderPrice) < 0) ||
                        (order.getSide() == Order.Side.SELL && order.getPrice().compareTo(oppositeOrderPrice) > 0)) {
                    addOrderToOrderbook(order, orderbookKey);
                    break;
                }
            }

            BigDecimal availableQuantity = order.getQuantity().subtract(order.getFilledQuantity());
            BigDecimal oppositeAvailableQuantity = oppositeOrder.getQuantity().subtract(oppositeOrder.getFilledQuantity());
            BigDecimal tradeQuantity = availableQuantity.min(oppositeAvailableQuantity);

            updateTrade(order, oppositeOrder, tradeQuantity);
            saveTrade(order, oppositeOrder, tradeQuantity, oppositeOrderPrice);

            updateOrderInRedis(oppositeOrder, oppositeOrderbookKey);
            if (!isMarketOrder) {
                updateOrderInRedis(order, orderbookKey);
            }
        }
    }

    private void updateTrade(Order order, Order oppositeOrder, BigDecimal tradeQuantity) {
        order.setFilledQuantity(order.getFilledQuantity().add(tradeQuantity));
        oppositeOrder.setFilledQuantity(oppositeOrder.getFilledQuantity().add(tradeQuantity));

        LocalDateTime currentTime = LocalDateTime.now();

        updateOrderStatus(oppositeOrder, currentTime);
        updateOrderStatus(order, currentTime);

        matchedOrderProducer.sendMatchedOrder(order);
        matchedOrderProducer.sendMatchedOrder(oppositeOrder);
    }

    private void updateOrderStatus(Order order, LocalDateTime currentTime) {
        Order.OrderStatus oldStatus = order.getStatus();
        if (order.getFilledQuantity().compareTo(order.getQuantity()) >= 0) {
            order.setStatus(Order.OrderStatus.COMPLETED);
            order.setUpdatedAt(currentTime);
            redisTemplate.delete("order:" + order.getId());
            sendWebSocketNotification(order.getUserId(), "ORDER_DELETED", order);
        } else if (order.getFilledQuantity().compareTo(BigDecimal.ZERO) > 0) {
            order.setStatus(Order.OrderStatus.PARTIALLY_FILLED);
            order.setUpdatedAt(currentTime);
            sendWebSocketNotification(order.getUserId(), "ORDER_UPDATED", order);
        }
    }

    private void updateOrderInRedis(Order order, String orderbookKey) {
        if (order.getStatus() != Order.OrderStatus.COMPLETED) {
            saveOrderToRedis(order);
        }
        redisTemplate.opsForZSet().remove(orderbookKey, order.getId());
        if (order.getStatus() == Order.OrderStatus.PARTIALLY_FILLED) {
            addOrderToOrderbook(order, orderbookKey);
        }
        sendOrderbookUpdate(order.getSymbol());
    }

    private void addOrderToOrderbook(Order order, String orderbookKey) {
        redisTemplate.opsForZSet().add(orderbookKey, order.getId(), order.getPrice().doubleValue());
        sendOrderbookUpdate(order.getSymbol());
    }

    private void sendOrderbookUpdate(String symbol) {
        Map<String, Object> orderbookSnapshot = orderbookService.getOrderbookSnapshot(symbol);
        orderbookWebSocketHandler.broadcastOrderbookUpdate(symbol, orderbookSnapshot);
    }

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

        // 只在訂單是PENDING時發送ORDER_CREATED事件
        if (order.getStatus() == Order.OrderStatus.PENDING) {
            sendWebSocketNotification(order.getUserId(), "ORDER_CREATED", order);
        }
    }

    private void saveTrade(Order buyOrder, Order sellOrder, BigDecimal tradeQuantity, BigDecimal price) {
        Trade trade = new Trade();
        trade.setId(generateTradeId());
        trade.setBuyOrder(buyOrder.getSide() == Order.Side.BUY ? buyOrder : sellOrder);
        trade.setSellOrder(buyOrder.getSide() == Order.Side.SELL ? buyOrder : sellOrder);
        trade.setQuantity(tradeQuantity);
        trade.setPrice(price);
        trade.setTradeTime(LocalDateTime.now());

        matchedOrderProducer.sendMatchedTrade(trade);
    }

    private String generateTradeId() {
        return String.valueOf(snowflakeIdGenerator.nextId());
    }

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

    private void sendWebSocketNotification(String userId, String eventType, Object data) {
        System.out.println("Send WebSocket notification to user: " + userId + ", event: " + eventType + ", data: " + data);
        webSocketNotificationProducer.sendNotification(userId, eventType, data);
    }
}