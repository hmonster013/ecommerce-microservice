package org.de013.paymentservice.client;

import org.junit.jupiter.api.Test;
import org.de013.paymentservice.exception.ExternalServiceException;
import org.de013.paymentservice.service.external.OrderValidationService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderServiceClientFallbackTest {
    @Test
    void readsReportUnavailableWithoutBody() {
        var fallback = new OrderServiceClientFallback();
        var byId = fallback.getOrderById(1L);
        var byNumber = fallback.getOrderByNumber("order");
        var total = fallback.getOrderTotal(1L);
        assertEquals(503, byId.getStatusCode().value());
        assertNull(byId.getBody());
        assertEquals(503, byNumber.getStatusCode().value());
        assertNull(byNumber.getBody());
        assertEquals(503, total.getStatusCode().value());
        assertNull(total.getBody());
    }

    @Test
    void orderValidationRejectsUnavailableTotal() {
        var service = new OrderValidationService(new OrderServiceClientFallback());
        assertThrows(ExternalServiceException.class, () -> service.getOrderTotal(1L));
    }

    @Test
    void mutationsReportUnavailable() {
        var fallback = new OrderServiceClientFallback();
        assertEquals(503, fallback.updateOrderStatus(1L, null).getStatusCode().value());
        assertEquals(503, fallback.markOrderAsPaid(1L, 2L, "payment").getStatusCode().value());
        assertEquals(503, fallback.markOrderPaymentFailed(1L, "failed").getStatusCode().value());
        assertEquals(503, fallback.releaseOrderReservation(1L).getStatusCode().value());
    }
}
