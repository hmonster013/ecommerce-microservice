package org.de013.orderservice.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompensationOrderTest {
    @Test void allowedPairsAndPositivePaidEvidence() {
        UUID operation = UUID.randomUUID();
        for (String pair : new String[] {"CANCELLED/CLOSED", "CANCELLED/CLOSURE_HOLD", "PENDING/CLOSURE_HOLD",
                "ON_HOLD/CLOSURE_HOLD", "PENDING/RECONCILIATION", "ON_HOLD/RECONCILIATION"}) {
            String[] parts = pair.split("/");
            assertNull(CompensationOrder.holdReason(new CompensationOrder.Facts(parts[0], parts[1], operation,
                    operation, null, null, false, false, true)));
        }
        assertEquals("PRIOR_PAID_OR_FULFILLED", CompensationOrder.holdReason(new CompensationOrder.Facts(
                "CANCELLED", "CLOSED", operation, operation, 1L, null, false, false, true)));
        assertEquals("PRIOR_PAID_OR_FULFILLED", CompensationOrder.holdReason(new CompensationOrder.Facts(
                "CANCELLED", "CLOSED", operation, operation, null, null, true, false, true)));
        assertEquals("LEGACY_HISTORY_UNPROVEN", CompensationOrder.holdReason(new CompensationOrder.Facts(
                "CANCELLED", "CLOSED", operation, operation, null, null, false, false, false)));
        assertEquals("ORDER_STATE_NOT_ALLOWED", CompensationOrder.holdReason(new CompensationOrder.Facts(
                "PAID", "CLOSED", operation, operation, null, null, false, false, true)));
        assertEquals("CLOSE_OPERATION_MISMATCH", CompensationOrder.holdReason(new CompensationOrder.Facts(
                "CANCELLED", "CLOSED", operation, UUID.randomUUID(), null, null, false, false, true)));
    }
}
