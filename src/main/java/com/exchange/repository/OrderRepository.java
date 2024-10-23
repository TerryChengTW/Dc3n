package com.exchange.repository;

import com.exchange.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    @Query("SELECT o FROM Order o WHERE o.userId = :userId " +
            "AND (:symbol IS NULL OR o.symbol = :symbol) " +
            "AND o.createdAt BETWEEN :startTime AND :endTime " +
            "AND (:orderType IS NULL OR o.orderType = :orderType) " +
            "AND (:side IS NULL OR o.side = :side) " +
            "AND (:status IS NULL OR o.status = :status) " +
            "ORDER BY o.createdAt DESC")
    List<Order> findOrderHistory(String userId,
                                 String symbol,
                                 Instant startTime,
                                 Instant endTime,
                                 Order.OrderType orderType,
                                 Order.Side side,
                                 Order.OrderStatus status);

    List<Order> findByUserIdAndUpdatedAtBetween(String userId, Instant startTime, Instant endTime);

}