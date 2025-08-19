package org.de013.orderservice.repository.custom;

import org.de013.orderservice.entity.OrderAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderAuditRepositoryCustom {

    Page<OrderAudit> searchAudit(Long orderId,
                                 String action,
                                 Long actorUserId,
                                 LocalDateTime start,
                                 LocalDateTime end,
                                 String ipContains,
                                 Pageable pageable);

    List<OrderAudit> findRecentActionsForOrder(Long orderId, int limit);
}

