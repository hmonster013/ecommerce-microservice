package org.de013.paymentservice.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request DTO for updating order status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusUpdateRequest {
    private String status;
    private String reason;
    private String updatedBy;
    private LocalDateTime updatedAt;

    // Additional fields for payment-related updates
    private Long paymentId;
    private String paymentNumber;
    private String paymentStatus;
}
