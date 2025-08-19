package org.de013.orderservice.redis.repository;

import org.de013.orderservice.redis.model.OrderCache;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderCacheRepository extends PagingAndSortingRepository<OrderCache, String>, CrudRepository<OrderCache, String> {
    Optional<OrderCache> findByOrderId(Long orderId);
    Optional<OrderCache> findByOrderNumber(String orderNumber);
    List<OrderCache> findByUserId(Long userId);
    List<OrderCache> findByStatus(String status);
}

