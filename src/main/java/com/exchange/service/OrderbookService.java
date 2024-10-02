package com.exchange.service;

import com.exchange.model.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OrderbookService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public OrderbookService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getOrderbookSnapshot(String symbol) {
        Map<String, Object> snapshot = new HashMap<>();
        // Assuming you get the latest trade price from another source or service
        BigDecimal latestTradePrice = getLatestTradePrice(symbol);

        // Define the price interval
        BigDecimal priceInterval = BigDecimal.TEN;

        // Get buy and sell orders
        Map<BigDecimal, BigDecimal> buySnapshot = getOrderSnapshot(symbol, "BUY", latestTradePrice, priceInterval, true);
        Map<BigDecimal, BigDecimal> sellSnapshot = getOrderSnapshot(symbol, "SELL", latestTradePrice, priceInterval, false);

        snapshot.put("buy", buySnapshot);
        snapshot.put("sell", sellSnapshot);

        return snapshot;
    }

    private Map<BigDecimal, BigDecimal> getOrderSnapshot(String symbol, String side, BigDecimal latestTradePrice, BigDecimal priceInterval, boolean isBuy) {
        Map<BigDecimal, BigDecimal> snapshot = new HashMap<>();

        // Redis key for buy/sell orders
        String redisKey = symbol + ":" + side;

        // Define the range for top 5 price intervals
        BigDecimal startPrice = isBuy ? latestTradePrice.subtract(priceInterval.multiply(BigDecimal.valueOf(5))) : latestTradePrice;
        BigDecimal endPrice = isBuy ? latestTradePrice : latestTradePrice.add(priceInterval.multiply(BigDecimal.valueOf(5)));

        // Get orders within the price range from Redis
        Set<String> orders = redisTemplate.opsForZSet().rangeByScore(redisKey, startPrice.doubleValue(), endPrice.doubleValue());

        if (orders != null) {
            for (String orderJson : orders) {
                try {
                    // Parse each order JSON and calculate the quantity sum per price interval
                    Order order = objectMapper.readValue(orderJson, Order.class);
                    BigDecimal orderPrice = order.getPrice();
                    BigDecimal orderQuantity = order.getUnfilledQuantity();

                    // Calculate the price interval key
                    BigDecimal intervalPrice = calculateIntervalPrice(orderPrice, priceInterval, isBuy);

                    // Aggregate the quantity in the corresponding price interval
                    snapshot.merge(intervalPrice, orderQuantity, BigDecimal::add);
                } catch (Exception e) {
                    e.printStackTrace(); // Handle parse exceptions appropriately
                }
            }
        }

        return snapshot;
    }

    private BigDecimal calculateIntervalPrice(BigDecimal orderPrice, BigDecimal priceInterval, boolean isBuy) {
        // Calculate the interval based on price and interval size
        BigDecimal intervalPrice = orderPrice.divide(priceInterval).setScale(0, isBuy ? BigDecimal.ROUND_FLOOR : BigDecimal.ROUND_CEILING);
        return intervalPrice.multiply(priceInterval);
    }

    private BigDecimal getLatestTradePrice(String symbol) {
        // Mock implementation to return the latest trade price
        // You should replace this with actual logic to get the latest trade price
        return new BigDecimal("500");
    }
}
