package org.de013.common.event;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentFailedPayload {
    private Long orderId;
    private String paymentNumber;
    private String userId;
    private String failureReason;
}
