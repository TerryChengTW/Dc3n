package com.exchange.repository;

import com.exchange.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    // 根據需要添加其他查詢方法
}
