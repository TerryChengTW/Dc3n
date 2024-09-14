package com.exchange.service;

import com.exchange.model.Order;
import com.exchange.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;

@Service
public class OrderMatchingService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private OrderRepository orderRepository;

    public void processOrder(Order order) {
        if (order.getOrderType() == Order.OrderType.MARKET) {
            matchMarketOrder(order);
        } else if (order.getOrderType() == Order.OrderType.LIMIT) {
            matchLimitOrder(order);
        }
    }

    private void matchMarketOrder(Order order) {
        String oppositeOrderbookKey = "orderbook:" + order.getSymbol() + ":" +
                (order.getSide() == Order.Side.BUY ? "SELL" : "BUY");

        while (order.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            Set<ZSetOperations.TypedTuple<String>> oppositeOrders = redisTemplate.opsForZSet()
                    .rangeWithScores(oppositeOrderbookKey, 0, 0);

            if (oppositeOrders == null || oppositeOrders.isEmpty()) {
                order.setOrderType(Order.OrderType.LIMIT);
                matchLimitOrder(order);
                break;
            }

            ZSetOperations.TypedTuple<String> oppositeOrderTuple = oppositeOrders.iterator().next();
            String oppositeOrderId = oppositeOrderTuple.getValue();
            BigDecimal oppositeOrderPrice = BigDecimal.valueOf(oppositeOrderTuple.getScore());

            Order oppositeOrder = getOrderFromRedis(oppositeOrderId);
            BigDecimal tradeQuantity = order.getQuantity().min(oppositeOrder.getQuantity());

            redisTemplate.opsForZSet().remove(oppositeOrderbookKey, oppositeOrderId);
            order.setQuantity(order.getQuantity().subtract(tradeQuantity));
            oppositeOrder.setQuantity(oppositeOrder.getQuantity().subtract(tradeQuantity));

            // 保存匹配成功的訂單到 MySQL
            saveMatchedOrdersToMySQL(order, oppositeOrder);
        }
    }

    private void matchLimitOrder(Order order) {
        String orderbookKey = "orderbook:" + order.getSymbol() + ":" + order.getSide();
        String oppositeOrderbookKey = "orderbook:" + order.getSymbol() + ":" +
                (order.getSide() == Order.Side.BUY ? "SELL" : "BUY");

        saveOrderToRedis(order);

        while (order.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            Set<ZSetOperations.TypedTuple<String>> oppositeOrders = redisTemplate.opsForZSet()
                    .rangeWithScores(oppositeOrderbookKey, 0, 0);

            if (oppositeOrders == null || oppositeOrders.isEmpty()) {
                redisTemplate.opsForZSet().add(orderbookKey, order.getId(), order.getPrice().doubleValue());
                break;
            }

            ZSetOperations.TypedTuple<String> oppositeOrderTuple = oppositeOrders.iterator().next();
            String oppositeOrderId = oppositeOrderTuple.getValue();
            BigDecimal oppositeOrderPrice = BigDecimal.valueOf(oppositeOrderTuple.getScore());

            if ((order.getSide() == Order.Side.BUY && order.getPrice().compareTo(oppositeOrderPrice) < 0) ||
                    (order.getSide() == Order.Side.SELL && order.getPrice().compareTo(oppositeOrderPrice) > 0)) {
                redisTemplate.opsForZSet().add(orderbookKey, order.getId(), order.getPrice().doubleValue());
                break;
            }

            Order oppositeOrder = getOrderFromRedis(oppositeOrderId);
            BigDecimal tradeQuantity = order.getQuantity().min(oppositeOrder.getQuantity());
            redisTemplate.opsForZSet().remove(oppositeOrderbookKey, oppositeOrderId);
            order.setQuantity(order.getQuantity().subtract(tradeQuantity));
            oppositeOrder.setQuantity(oppositeOrder.getQuantity().subtract(tradeQuantity));

            // 保存匹配成功的訂單到 MySQL
            saveMatchedOrdersToMySQL(order, oppositeOrder);
        }
    }

    private void saveOrderToRedis(Order order) {
        String orderKey = "order:" + order.getId();
        redisTemplate.opsForHash().put(orderKey, "id", order.getId());
        redisTemplate.opsForHash().put(orderKey, "symbol", order.getSymbol());
        redisTemplate.opsForHash().put(orderKey, "price", order.getPrice().toString());
        redisTemplate.opsForHash().put(orderKey, "quantity", order.getQuantity().toString());
        redisTemplate.opsForHash().put(orderKey, "side", order.getSide().toString());
        redisTemplate.opsForHash().put(orderKey, "orderType", order.getOrderType().toString());
        redisTemplate.opsForHash().put(orderKey, "createdAt", order.getCreatedAt().toString());
    }

    // 使用 JPA 保存匹配成功的訂單
    private void saveMatchedOrdersToMySQL(Order order, Order oppositeOrder) {
        System.out.println("Order symbol: " + order.getSymbol());
        System.out.println("Opposite Order symbol: " + oppositeOrder.getSymbol());

        order.setStatus(Order.OrderStatus.COMPLETED);
        oppositeOrder.setStatus(Order.OrderStatus.COMPLETED);

        // 使用 JPA 保存到 MySQL
        orderRepository.save(order);
        orderRepository.save(oppositeOrder);
    }


    public Order getOrderFromRedis(String orderId) {
        String orderKey = "order:" + orderId;
        String priceStr = (String) redisTemplate.opsForHash().get(orderKey, "price");
        String quantityStr = (String) redisTemplate.opsForHash().get(orderKey, "quantity");
        String orderTypeStr = (String) redisTemplate.opsForHash().get(orderKey, "orderType");
        String sideStr = (String) redisTemplate.opsForHash().get(orderKey, "side");
        String symbolStr = (String) redisTemplate.opsForHash().get(orderKey, "symbol");  // 確認從 Redis 讀取 symbol

        // 檢查所有必須的屬性
        if (priceStr == null || quantityStr == null || orderTypeStr == null || sideStr == null || symbolStr == null) {
            throw new IllegalStateException("Redis 中的訂單數據缺失: " + orderId);
        }

        // 建立 Order 實例並設置屬性
        Order order = new Order();
        order.setId(orderId);
        order.setPrice(new BigDecimal(priceStr));
        order.setQuantity(new BigDecimal(quantityStr));
        order.setOrderType(Order.OrderType.valueOf(orderTypeStr));
        order.setSide(Order.Side.valueOf(sideStr));
        order.setSymbol(symbolStr);  // 設置 symbol
        System.out.println("Fetched order from Redis, symbol: " + symbolStr);
        return order;
    }

}
