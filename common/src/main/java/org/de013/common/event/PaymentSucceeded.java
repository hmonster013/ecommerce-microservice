package org.de013.common.event;

import java.math.BigDecimal;

/** Existing success v1 wire contract; transition revision stays in the outbox. */
public record PaymentSucceeded(String eventId, PaymentEventType eventType, int schemaVersion,
                               String occurredAt, Long paymentId, String paymentNumber,
                               Long orderId, String provider, BigDecimal amount, String currency) { }
