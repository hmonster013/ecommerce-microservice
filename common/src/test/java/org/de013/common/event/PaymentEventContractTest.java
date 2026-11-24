package org.de013.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PaymentEventContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void successRetainsOriginalV1FieldsAndFailureAddsTransitionIdentity() throws Exception {
        var success = new PaymentSucceeded("event", PaymentEventType.PaymentSucceeded, 1,
                "2026-11-16T00:00:00Z", 1L, "PAY-1", 2L, "STRIPE", new BigDecimal("12.34"), "USD");
        var json = mapper.readTree(mapper.writeValueAsString(success));
        Set<String> fields = new HashSet<>();
        json.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("eventId", "eventType", "schemaVersion", "occurredAt", "paymentId",
                "paymentNumber", "orderId", "provider", "amount", "currency"), fields);
        assertEquals("PaymentSucceeded", json.path("eventType").asText());
        assertEquals(new BigDecimal("12.34"), json.path("amount").decimalValue());
        assertFalse(json.has("revision"));
        assertEquals("payment.succeeded.v1", PaymentEventType.PaymentSucceeded.topic());

        for (var type : new PaymentEventType[]{PaymentEventType.PaymentFailed, PaymentEventType.PaymentCanceled}) {
            String status = type == PaymentEventType.PaymentFailed ? "FAILED" : "CANCELED";
            var observation = new PaymentTransitionEvent("observation", type, 1,
                    success.occurredAt(), 1L, "PAY-1", 2L, "STRIPE", success.amount(), "USD",
                    3L, "PENDING", status);
            var observed = mapper.readTree(mapper.writeValueAsString(observation));
            assertEquals(type.name(), observed.path("eventType").asText());
            assertEquals(3L, observed.path("revision").asLong());
            assertEquals("PENDING", observed.path("previousStatus").asText());
            assertEquals(status, observed.path("status").asText());
            assertEquals(type == PaymentEventType.PaymentFailed ? "payment.failed.v1" : "payment.canceled.v1", type.topic());
        }
    }
}
