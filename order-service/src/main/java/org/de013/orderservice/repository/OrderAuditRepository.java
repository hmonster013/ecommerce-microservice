package org.de013.orderservice.repository;

import org.de013.orderservice.entity.OrderAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderAuditRepository extends JpaRepository<OrderAudit, Long>, JpaSpecificationExecutor<OrderAudit> {

    Page<OrderAudit> findByOrderIdOrderByActionAtDesc(Long orderId, Pageable pageable);

    List<OrderAudit> findByOrderIdOrderByActionAtDesc(Long orderId);

    Page<OrderAudit> findByActorUserId(Long actorUserId, Pageable pageable);

    Page<OrderAudit> findByAction(String action, Pageable pageable);

    Page<OrderAudit> findByActionAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    @Query("SELECT oa.action, COUNT(oa) FROM OrderAudit oa WHERE oa.actionAt BETWEEN :start AND :end GROUP BY oa.action")
    List<Object[]> countActionsByType(LocalDateTime start, LocalDateTime end);
}

