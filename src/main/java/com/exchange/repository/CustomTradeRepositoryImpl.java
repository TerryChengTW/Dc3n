package com.exchange.repository;

import com.exchange.model.Order;
import com.exchange.model.Trade;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Repository
public class CustomTradeRepositoryImpl implements CustomTradeRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void saveAllOrdersAndTrades(List<Order> buyOrders, List<Order> sellOrders, List<Trade> trades) {
        // 合併買單與賣單
        List<Order> allOrders = new ArrayList<>();
        allOrders.addAll(buyOrders);
        allOrders.addAll(sellOrders);

        // 使用批量插入/更新 Order
        String orderUpsertQuery = "INSERT INTO orders (id, user_id, symbol, price, quantity, filled_quantity, unfilled_quantity, side, order_type, status, stop_price, take_profit_price, created_at, updated_at, modified_at) " +
                "VALUES (:id, :userId, :symbol, :price, :quantity, :filledQuantity, :unfilledQuantity, :side, :orderType, :status, :stopPrice, :takeProfitPrice, :createdAt, :updatedAt, :modifiedAt) " +
                "ON DUPLICATE KEY UPDATE " +
                "price = VALUES(price), quantity = VALUES(quantity), " +
                "filled_quantity = VALUES(filled_quantity), unfilled_quantity = VALUES(unfilled_quantity), " +
                "side = VALUES(side), order_type = VALUES(order_type), status = VALUES(status), " +
                "stop_price = VALUES(stop_price), take_profit_price = VALUES(take_profit_price), " +
                "updated_at = VALUES(updated_at), modified_at = VALUES(modified_at)";

        // 批量執行 Order 的 upsert 操作
        for (Order order : allOrders) {
            entityManager.createNativeQuery(orderUpsertQuery)
                    .setParameter("id", order.getId())
                    .setParameter("userId", order.getUserId())
                    .setParameter("symbol", order.getSymbol())
                    .setParameter("price", order.getPrice())
                    .setParameter("quantity", order.getQuantity())
                    .setParameter("filledQuantity", order.getFilledQuantity())
                    .setParameter("unfilledQuantity", order.getUnfilledQuantity())
                    .setParameter("side", order.getSide().name())
                    .setParameter("orderType", order.getOrderType().name())
                    .setParameter("status", order.getStatus().name())
                    .setParameter("stopPrice", order.getStopPrice())
                    .setParameter("takeProfitPrice", order.getTakeProfitPrice())
                    .setParameter("createdAt", order.getCreatedAt())
                    .setParameter("updatedAt", order.getUpdatedAt())
                    .setParameter("modifiedAt", order.getModifiedAt())
                    .executeUpdate();
        }

        // 批量插入 Trade
        String tradeInsertQuery = "INSERT INTO trades (id, symbol, price, quantity, buy_order_id, sell_order_id, trade_time, direction) " +
                "VALUES (:id, :symbol, :price, :quantity, :buyOrderId, :sellOrderId, :tradeTime, :direction)";

        for (Trade trade : trades) {
            entityManager.createNativeQuery(tradeInsertQuery)
                    .setParameter("id", trade.getId())
                    .setParameter("symbol", trade.getSymbol())
                    .setParameter("price", trade.getPrice())
                    .setParameter("quantity", trade.getQuantity())
                    .setParameter("buyOrderId", trade.getBuyOrder().getId())
                    .setParameter("sellOrderId", trade.getSellOrder().getId())
                    .setParameter("tradeTime", trade.getTradeTime())
                    .setParameter("direction", trade.getDirection())
                    .executeUpdate();
        }

        // 在最後 flush 以提交所有變更
        entityManager.flush();
    }
}
