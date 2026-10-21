package org.de013.paymentservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "hosted_checkouts")
@Data
public class HostedCheckout {
    @Id
    @Column(name = "order_id")
    private Long orderId;
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;
    @Column(name = "payment_id", nullable = false, unique = true)
    private Long paymentId;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(name = "success_url", nullable = false, length = 500)
    private String successUrl;
    @Column(name = "cancel_url", nullable = false, length = 500)
    private String cancelUrl;
    @Column(nullable = false)
    private int generation;
    @Column(name = "session_id", unique = true, length = 255)
    private String sessionId;
    @Column(name = "session_state", nullable = false, length = 20)
    private String sessionState = "UNKNOWN";
    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;
}
