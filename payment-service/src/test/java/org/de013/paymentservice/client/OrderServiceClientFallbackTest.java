package org.de013.paymentservice.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderServiceClientFallbackTest {
    @Test
    void mutationsReportUnavailable() {
        var fallback = new OrderServiceClientFallback();
        assertEquals(503, fallback.updateOrderStatus(1L, null).getStatusCode().value());
        assertEquals(503, fallback.markOrderAsPaid(1L, 2L, "payment").getStatusCode().value());
        assertEquals(503, fallback.markOrderPaymentFailed(1L, "failed").getStatusCode().value());
        assertEquals(503, fallback.releaseOrderReservation(1L).getStatusCode().value());
    }
}
