package org.de013.paymentservice.dto.external;

import java.time.LocalDateTime;

/**
 * Request DTO for updating order status
 */
public record OrderStatusUpdateRequest(
        String status,
        String reason,
        String updatedBy,
        LocalDateTime updatedAt,

        // Additional fields for payment-related updates
        Long paymentId,
        String paymentNumber,
        String paymentStatus
) {
    public OrderStatusUpdateRequest(String status, String reason) {
        this(status, reason, null, null, null, null, null);
    }

    public OrderStatusUpdateRequest(String status, String reason, String updatedBy) {
        this(status, reason, updatedBy, null, null, null, null);
    }
}
