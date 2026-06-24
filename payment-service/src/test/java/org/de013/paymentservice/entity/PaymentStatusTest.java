package org.de013.paymentservice.entity;

import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentStatusTest {

    @Test
    void testPaymentStatusEnumValues() {
        assertEquals("SUCCEEDED", PaymentStatus.SUCCEEDED.name());
        assertEquals("FAILED", PaymentStatus.FAILED.name());
        assertEquals("PENDING", PaymentStatus.PENDING.name());
        assertEquals("CANCELED", PaymentStatus.CANCELED.name());
        assertEquals("PROCESSING", PaymentStatus.PROCESSING.name());
    }
}
