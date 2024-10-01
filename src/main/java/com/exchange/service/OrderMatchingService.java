//package com.exchange.service;
//
//import com.exchange.dto.TradeDTO;
//import com.exchange.model.Order;
//import com.exchange.model.Trade;
//import com.exchange.producer.MatchedOrderProducer;
//import com.exchange.producer.WebSocketNotificationProducer;
//import com.exchange.utils.OrderProcessingData;
//import com.exchange.utils.OrderProcessingTracker;
//import com.exchange.utils.SnowflakeIdGenerator;
//import com.exchange.websocket.OrderbookWebSocketHandler;
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.data.redis.core.ZSetOperations;
//import org.springframework.kafka.core.KafkaTemplate;
//import org.springframework.stereotype.Service;
//
//import java.math.BigDecimal;
//import java.time.Instant;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Set;
//
//@Service
//public class OrderMatchingService {
//
//    @Autowired
//    private RedisTemplate<String, String> redisTemplate;
//
//    @Autowired
//    private KafkaTemplate<String, String> kafkaTemplate;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    @Autowired
//    private MatchedOrderProducer matchedOrderProducer;
//
//    @Autowired
//    private WebSocketNotificationProducer webSocketNotificationProducer;
//
//    @Autowired
//    private SnowflakeIdGenerator snowflakeIdGenerator;
//
//    @Autowired
//    private OrderbookWebSocketHandler orderbookWebSocketHandler;
//
//    @Autowired
//    private OrderbookService orderbookService;
//
//    public void processOrder(Order order) throws JsonProcessingException {
//        if (order.getOrderType() == Order.OrderType.MARKET) {
//            matchMarketOrder(order);
//        } else if (order.getOrderType() == Order.OrderType.LIMIT) {
//            matchLimitOrder(order);
//        }
//    }
//
//    private void matchMarketOrder(Order order) throws JsonProcessingException {
//        matchOrder(order, true);
//    }
//
//    private void matchLimitOrder(Order order) throws JsonProcessingException {
//        matchOrder(order, false);
//        // 檢查是否有未匹配的數量，如果還有剩餘的數量，則將訂單放入 Redis
//        if (order.getQuantity().subtract(order.getFilledQuantity()).compareTo(BigDecimal.ZERO) > 0) {
//            saveOrderToRedis(order);
//            addOrderToOrderbook(order, "orderbook:" + order.getSymbol() + ":" + order.getSide());
//        }
//    }
//
//    private void matchOrder(Order order, boolean isMarketOrder) throws JsonProcessingException {
//        String orderbookKey = "orderbook:" + order.getSymbol() + ":" + order.getSide();
//        String oppositeOrderbookKey = "orderbook:" + order.getSymbol() + ":" + (order.getSide() == Order.Side.BUY ? "SELL" : "BUY");
//
//        OrderProcessingData processingData = new OrderProcessingData();
//        processingData.setOrderId(order.getId());
//
//        long totalRedisFetchTime = 0;
//        long totalTradeUpdateTime = 0;
//        long totalRedisUpdateTime = 0;
//        long totalGetOrderFromRedisTime = 0;
//        long totalAddOrderToOrderbookTime = 0;
//        long totalBigDecimalOperationTime = 0;
//        long totalObjectCreationTime = 0;
//
//        long methodStartTime = System.nanoTime();
//
//        // 記錄對象創建的時間
//        long objectCreationStartTime = System.nanoTime();
//        ZSetOperations.TypedTuple<String> oppositeOrderTuple = null;
//        Set<ZSetOperations.TypedTuple<String>> oppositeOrders = null;
//        totalObjectCreationTime += (System.nanoTime() - objectCreationStartTime);
//
//        while (order.getQuantity().subtract(order.getFilledQuantity()).compareTo(BigDecimal.ZERO) > 0) {
//            // 記錄 Redis 抓取對手單的時間
//            long redisFetchStartTime = System.nanoTime();
//            if (order.getSide() == Order.Side.BUY) {
//                oppositeOrders = redisTemplate.opsForZSet().rangeWithScores(oppositeOrderbookKey, 0, 0);
//            } else {
//                oppositeOrders = redisTemplate.opsForZSet().reverseRangeWithScores(oppositeOrderbookKey, 0, 0);
//            }
//            totalRedisFetchTime += (System.nanoTime() - redisFetchStartTime);
//
//            if (oppositeOrders == null || oppositeOrders.isEmpty()) {
//                if (!isMarketOrder) {
//                    long addOrderToOrderbookStartTime = System.nanoTime();
//                    addOrderToOrderbook(order, orderbookKey);
//                    totalAddOrderToOrderbookTime += (System.nanoTime() - addOrderToOrderbookStartTime);
//                }
//                break;
//            }
//
//            // 記錄對象創建的時間
//            objectCreationStartTime = System.nanoTime();
//            oppositeOrderTuple = oppositeOrders.iterator().next();
//            String oppositeOrderId = oppositeOrderTuple.getValue();
//            totalObjectCreationTime += (System.nanoTime() - objectCreationStartTime);
//
//            // 記錄 getOrderFromRedis 的時間
//            long getOrderFromRedisStartTime = System.nanoTime();
//            Order oppositeOrder = getOrderFromRedis(oppositeOrderId);
//            totalGetOrderFromRedisTime += (System.nanoTime() - getOrderFromRedisStartTime);
//
//            // 記錄 BigDecimal 運算時間
//            long bigDecimalOperationStartTime = System.nanoTime();
//            BigDecimal oppositeOrderPrice = BigDecimal.valueOf(oppositeOrderTuple.getScore());
//            totalBigDecimalOperationTime += (System.nanoTime() - bigDecimalOperationStartTime);
//
//            if (!isMarketOrder) {
//                if ((order.getSide() == Order.Side.BUY && order.getPrice().compareTo(oppositeOrderPrice) < 0) ||
//                        (order.getSide() == Order.Side.SELL && order.getPrice().compareTo(oppositeOrderPrice) > 0)) {
//                    long addOrderToOrderbookStartTime = System.nanoTime();
//                    addOrderToOrderbook(order, orderbookKey);
//                    totalAddOrderToOrderbookTime += (System.nanoTime() - addOrderToOrderbookStartTime);
//                    break;
//                }
//            }
//
//            // 記錄 BigDecimal 運算時間
//            bigDecimalOperationStartTime = System.nanoTime();
//            BigDecimal availableQuantity = order.getQuantity().subtract(order.getFilledQuantity());
//            BigDecimal oppositeAvailableQuantity = oppositeOrder.getQuantity().subtract(oppositeOrder.getFilledQuantity());
//            BigDecimal tradeQuantity = availableQuantity.min(oppositeAvailableQuantity);
//            totalBigDecimalOperationTime += (System.nanoTime() - bigDecimalOperationStartTime);
//
//            // 記錄交易更新的時間
//            long tradeUpdateStartTime = System.nanoTime();
//            updateTrade(order, oppositeOrder, tradeQuantity);
//            saveTrade(order, oppositeOrder, tradeQuantity, oppositeOrderPrice);
//            totalTradeUpdateTime += (System.nanoTime() - tradeUpdateStartTime);
//
//            // 記錄 Redis 更新的時間
//            long redisUpdateStartTime = System.nanoTime();
//            System.out.println("Redis update start time1: " + redisUpdateStartTime);
//            updateOrderInRedis(oppositeOrder, oppositeOrderbookKey);
//            long RedisUpdateTime1 = (System.nanoTime() - redisUpdateStartTime);
//            System.out.println("Redis update time1: " + RedisUpdateTime1);
//            long RedisUpdateTime2 = 0;
//            if (!isMarketOrder) {
//                redisUpdateStartTime = System.nanoTime();
//                System.out.println("Redis update start time2: " + redisUpdateStartTime);
//                updateOrderInRedis(order, orderbookKey);
//                RedisUpdateTime2 = (System.nanoTime() - redisUpdateStartTime);
//                System.out.println("Redis update time2: " + RedisUpdateTime2);
//            }
//            totalRedisUpdateTime = RedisUpdateTime1 + RedisUpdateTime2;
//        }
//
//        long methodEndTime = System.nanoTime();
//        long totalProcessingTime = methodEndTime - methodStartTime;
//
//        // 記錄未追蹤的時間 (Untracked Time)
//        long trackedTime = totalRedisFetchTime + totalTradeUpdateTime + totalRedisUpdateTime + totalGetOrderFromRedisTime +
//                totalAddOrderToOrderbookTime + totalBigDecimalOperationTime + totalObjectCreationTime;
//        long untrackedTime = totalProcessingTime - trackedTime;
//
//        // 設置處理時間
//        processingData.setRedisFetchTime(totalRedisFetchTime);
//        processingData.setTradeUpdateTime(totalTradeUpdateTime);
//        processingData.setRedisUpdateTime(totalRedisUpdateTime);
//        processingData.setGetOrderFromRedisTime(totalGetOrderFromRedisTime);
//        processingData.setAddOrderToOrderbookTime(totalAddOrderToOrderbookTime);
//        processingData.setBigDecimalOperationTime(totalBigDecimalOperationTime);
//        processingData.setObjectCreationTime(totalObjectCreationTime);
//        processingData.setTotalProcessingTime(totalProcessingTime);
//        processingData.setUntrackedTime(untrackedTime);
//
//        System.out.println("Order processing time: " + totalProcessingTime + " ns"
//                + ", Redis fetch time: " + totalRedisFetchTime + " ns"
//                + ", Trade update time: " + totalTradeUpdateTime + " ns"
//                + ", Redis update time: " + totalRedisUpdateTime + " ns"
//                + ", Get order from Redis time: " + totalGetOrderFromRedisTime + " ns"
//                + ", Add order to orderbook time: " + totalAddOrderToOrderbookTime + " ns"
//                + ", BigDecimal operation time: " + totalBigDecimalOperationTime + " ns"
//                + ", Object creation time: " + totalObjectCreationTime + " ns"
//                + ", Untracked time: " + untrackedTime + " ns");
//
//        // 結束追蹤並記錄
//        OrderProcessingTracker.endTracking(order.getId(), processingData);
//    }
//
//
//
//
//    private void updateTrade(Order order, Order oppositeOrder, BigDecimal tradeQuantity) {
//        order.setFilledQuantity(order.getFilledQuantity().add(tradeQuantity));
//        oppositeOrder.setFilledQuantity(oppositeOrder.getFilledQuantity().add(tradeQuantity));
//
//        Instant currentTime = Instant.now();
//
//        updateOrderStatus(oppositeOrder, currentTime);
//        updateOrderStatus(order, currentTime);
//
//        matchedOrderProducer.sendMatchedOrder(order);
//        matchedOrderProducer.sendMatchedOrder(oppositeOrder);
//    }
//
//    private void updateOrderStatus(Order order, Instant currentTime) {
//        Order.OrderStatus oldStatus = order.getStatus();
//        if (order.getFilledQuantity().compareTo(order.getQuantity()) >= 0) {
//            order.setStatus(Order.OrderStatus.COMPLETED);
//            order.setUpdatedAt(currentTime);
//            redisTemplate.delete("order:" + order.getId());
//            sendWebSocketNotification(order.getUserId(), "ORDER_COMPLETED", order);
//        } else if (order.getFilledQuantity().compareTo(BigDecimal.ZERO) > 0) {
//            order.setStatus(Order.OrderStatus.PARTIALLY_FILLED);
//            order.setUpdatedAt(currentTime);
//            sendWebSocketNotification(order.getUserId(), "ORDER_UPDATED", order);
//        }
//    }
//
//    private void updateOrderInRedis(Order order, String orderbookKey) {
//        if (order.getStatus() != Order.OrderStatus.COMPLETED) {
//            long saveOrderStartTime = System.nanoTime();
//            saveOrderToRedis(order);
//            long saveOrderEndTime = System.nanoTime();
//            System.out.println("Save order time: " + (saveOrderEndTime - saveOrderStartTime) + " ns");
//        }
//        long removeOrderStartTime = System.nanoTime();
//        redisTemplate.opsForZSet().remove(orderbookKey, order.getId());
//        long removeOrderEndTime = System.nanoTime();
//        System.out.println("Remove order time: " + (removeOrderEndTime - removeOrderStartTime) + " ns");
//        if (order.getStatus() == Order.OrderStatus.PARTIALLY_FILLED) {
//            long addOrderToOrderbookStartTime = System.nanoTime();
//            addOrderToOrderbook(order, orderbookKey);
//            long addOrderToOrderbookEndTime = System.nanoTime();
//            System.out.println("Add order to orderbook time: " + (addOrderToOrderbookEndTime - addOrderToOrderbookStartTime) + " ns");
//        }
//        long sendOrderbookUpdateStartTime = System.nanoTime();
//        sendOrderbookUpdate(order.getSymbol());
//        long sendOrderbookUpdateEndTime = System.nanoTime();
//        System.out.println("Send orderbook update time: " + (sendOrderbookUpdateEndTime - sendOrderbookUpdateStartTime) + " ns");
//    }
//
//    private void addOrderToOrderbook(Order order, String orderbookKey) {
//        redisTemplate.opsForZSet().add(orderbookKey, order.getId(), order.getPrice().doubleValue());
//        sendOrderbookUpdate(order.getSymbol());
//    }
//
//    void sendOrderbookUpdate(String symbol) {
//        Map<String, Object> orderbookSnapshot = orderbookService.getOrderbookSnapshot(symbol);
//        orderbookWebSocketHandler.broadcastOrderbookUpdate(symbol, orderbookSnapshot);
//    }
//
//    private void saveOrderToRedis(Order order) {
//        String orderKey = "order:" + order.getId();
//        Map<String, Object> orderMap = new HashMap<>();
//        orderMap.put("id", order.getId());
//        orderMap.put("userId", order.getUserId());
//        orderMap.put("symbol", order.getSymbol());
//        orderMap.put("price", order.getPrice().toString());
//        orderMap.put("quantity", order.getQuantity().toString());
//        orderMap.put("filledQuantity", order.getFilledQuantity().toString());
//        orderMap.put("side", order.getSide().toString());
//        orderMap.put("orderType", order.getOrderType().toString());
//        orderMap.put("status", order.getStatus().toString());
//        orderMap.put("createdAt", order.getCreatedAt().toString());
//        orderMap.put("updatedAt", order.getUpdatedAt().toString());
//        orderMap.put("modifiedAt", order.getModifiedAt().toString());
//
//        // 一次性將所有欄位寫入 Redis
//        redisTemplate.opsForHash().putAll(orderKey, orderMap);
//
//        if (order.getStatus() == Order.OrderStatus.PENDING) {
//            sendWebSocketNotification(order.getUserId(), "ORDER_CREATED", order);
//        }
//    }
//
//    private void saveTrade(Order buyOrder, Order sellOrder, BigDecimal tradeQuantity, BigDecimal price) throws JsonProcessingException {
//        Trade trade = new Trade();
//        trade.setId(generateTradeId());
//        trade.setBuyOrder(buyOrder.getSide() == Order.Side.BUY ? buyOrder : sellOrder);
//        trade.setSellOrder(buyOrder.getSide() == Order.Side.SELL ? buyOrder : sellOrder);
//        trade.setSymbol(buyOrder.getSymbol());
//        trade.setQuantity(tradeQuantity);
//        trade.setPrice(price);
//        trade.setTradeTime(Instant.now()); // 獲取當前的時間戳
//
//        matchedOrderProducer.sendMatchedTrade(trade);
//
//        TradeDTO tradeDTO = new TradeDTO(trade.getSymbol(), trade.getTradeTime(), trade.getPrice(), trade.getQuantity());
//
//        // 構建Kafka消息，包含時間戳
//        String simpleMessage = "{\"symbol\": \"" + trade.getSymbol() + "\", \"price\": " + trade.getPrice() + ", \"tradeTime\": " + trade.getTradeTime().getEpochSecond() + "}";
//
//        kafkaTemplate.send("recent-trades", trade.getSymbol(), objectMapper.writeValueAsString(tradeDTO));
//        kafkaTemplate.send("kline-updates", trade.getSymbol(), simpleMessage); // 將tradeTime包含進去
//    }
//
//
//    private String generateTradeId() {
//        return String.valueOf(snowflakeIdGenerator.nextId());
//    }
//
//    public Order getOrderFromRedis(String orderId) {
//        String orderKey = "order:" + orderId;
//        String priceStr = (String) redisTemplate.opsForHash().get(orderKey, "price");
//        String userIdStr = (String) redisTemplate.opsForHash().get(orderKey, "userId");
//        String quantityStr = (String) redisTemplate.opsForHash().get(orderKey, "quantity");
//        String quantityFilledStr = (String) redisTemplate.opsForHash().get(orderKey, "filledQuantity");
//        String orderTypeStr = (String) redisTemplate.opsForHash().get(orderKey, "orderType");
//        String sideStr = (String) redisTemplate.opsForHash().get(orderKey, "side");
//        String symbolStr = (String) redisTemplate.opsForHash().get(orderKey, "symbol");
//        String statusStr = (String) redisTemplate.opsForHash().get(orderKey, "status");
//        String createdAtStr = (String) redisTemplate.opsForHash().get(orderKey, "createdAt");
//        String updatedAtStr = (String) redisTemplate.opsForHash().get(orderKey, "updatedAt");
//        String modifiedAtStr = (String) redisTemplate.opsForHash().get(orderKey, "modifiedAt");
//
//        if (priceStr == null || quantityStr == null || orderTypeStr == null || sideStr == null ||
//                symbolStr == null || userIdStr == null || statusStr == null || createdAtStr == null || updatedAtStr == null) {
//            return null;
//        }
//
//        Order order = new Order();
//        order.setId(orderId);
//        order.setUserId(userIdStr);
//        order.setPrice(new BigDecimal(priceStr));
//        order.setQuantity(new BigDecimal(quantityStr));
//        order.setFilledQuantity(new BigDecimal(quantityFilledStr));
//        order.setOrderType(Order.OrderType.valueOf(orderTypeStr));
//        order.setSide(Order.Side.valueOf(sideStr));
//        order.setSymbol(symbolStr);
//        order.setStatus(Order.OrderStatus.valueOf(statusStr));
//        order.setCreatedAt(Instant.parse(createdAtStr));
//        order.setUpdatedAt(Instant.parse(updatedAtStr));
//        order.setModifiedAt(Instant.parse(modifiedAtStr));
//
//        return order;
//    }
//
//    void sendWebSocketNotification(String userId, String eventType, Object data) {
////        System.out.println("Send WebSocket notification to user: " + userId + ", event: " + eventType + ", data: " + data);
//        webSocketNotificationProducer.sendNotification(userId, eventType, data);
//    }
//}