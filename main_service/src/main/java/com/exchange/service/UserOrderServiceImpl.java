package com.exchange.service;

import com.exchange.dto.OrderDTO;
import com.exchange.repository.RedisOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserOrderServiceImpl implements UserOrderService {

    private final RedisOrderRepository redisOrderRepository;

    @Autowired
    public UserOrderServiceImpl(RedisOrderRepository redisOrderRepository) {
        this.redisOrderRepository = redisOrderRepository;
    }

    @Override
    public List<OrderDTO> getUserOrders(String userId) {
        return redisOrderRepository.getUserOrders(userId);
    }
}