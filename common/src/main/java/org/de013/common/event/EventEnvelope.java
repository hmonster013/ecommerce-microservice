package org.de013.common.event;

import lombok.*;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventEnvelope<T> {
    private String eventId;        // UUID, khóa idempotency
    private String eventType;      // vd "payment.succeeded"
    private String aggregateType;  // vd "PAYMENT"
    private String aggregateId;    // vd paymentNumber / orderId
    private Instant occurredAt;
    private T payload;
}
