package org.de013.paymentservice.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for Order data from Order Service
 */
@Data
public class OrderDto {
    private Long id;
    private String orderNumber;
    private String userId;
    private MoneyDto totalAmount;
    private String currency;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @lombok.Data
    public static class MoneyDto {
        private BigDecimal amount;
        private String currency;
    }

    // Helper methods
    @JsonIgnore
    public boolean isPaid() {
        return "PAID".equals(status);
    }

    @JsonIgnore
    public boolean isPending() {
        return "PENDING".equals(status);
    }

    @JsonIgnore
    public boolean isCanceled() {
        return "CANCELED".equals(status);
    }
}

