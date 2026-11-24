package org.de013.orderservice.dto.request;

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
}
