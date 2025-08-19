package org.de013.orderservice.redis.repository;

import org.de013.orderservice.redis.model.OrderSession;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderSessionRepository extends CrudRepository<OrderSession, String> {
    List<OrderSession> findByOrderId(Long orderId);
    List<OrderSession> findByUserId(Long userId);
}

