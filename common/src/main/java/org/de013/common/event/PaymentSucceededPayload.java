package org.de013.common.event;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentSucceededPayload {
    private Long orderId;
    private Long paymentId;
    private String paymentNumber;
    private String userId;
    private BigDecimal amount;
    private String currency;
    private String receiptEmail;
}
