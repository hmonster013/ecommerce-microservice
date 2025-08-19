package org.de013.orderservice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.de013.common.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * Order Audit Entity
 * 
 * Tracks audit trail events for orders (who, what, when, where).
 */
@Entity
@Table(name = "order_audit", indexes = {
        @Index(name = "idx_order_audit_order", columnList = "order_id"),
        @Index(name = "idx_order_audit_action_at", columnList = "action_at"),
        @Index(name = "idx_order_audit_action", columnList = "action")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderAudit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "action", length = 100, nullable = false)
    @NotBlank
    private String action; // e.g., CREATED, UPDATED_STATUS, CANCELLED, REFUNDED

    @Column(name = "details", length = 4000)
    private String details;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_role", length = 100)
    private String actorRole; // CUSTOMER, ADMIN, SYSTEM

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    @Column(name = "metadata", length = 2000)
    private String metadata;

    @Column(name = "action_at")
    @NotNull
    private LocalDateTime actionAt;
}

