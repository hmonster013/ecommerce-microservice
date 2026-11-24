package org.de013.orderservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "checkout_attempts")
@Getter
@NoArgsConstructor
public class CheckoutAttempt {
    @Id
    @Column(name = "attempt_key", length = 36)
    private String attemptKey;
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;
    @Column(name = "request_snapshot")
    private String requestSnapshot;

    public CheckoutAttempt(String attemptKey, String userId) {
        this.attemptKey = attemptKey;
        this.userId = userId;
    }
}
