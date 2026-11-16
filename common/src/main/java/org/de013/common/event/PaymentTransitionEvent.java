package org.de013.common.event;

import java.math.BigDecimal;

/** Failure/cancel observation, not authorization to release inventory or refund. */
public record PaymentTransitionEvent(String eventId, PaymentEventType eventType, int schemaVersion,
                                     String occurredAt, Long paymentId, String paymentNumber,
                                     Long orderId, String provider, BigDecimal amount, String currency,
                                     long revision, String previousStatus, String status) { }
