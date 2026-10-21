package org.de013.paymentservice.service;

import org.de013.paymentservice.repository.HostedCheckoutRepository;
import org.de013.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.cloud.bus.enabled=false")
@ActiveProfiles("test")
class HostedCheckoutStoreTest {
    @Autowired HostedCheckoutStore store;
    @Autowired HostedCheckoutRepository checkouts;
    @Autowired PaymentRepository payments;

    @Test void commitsPaymentBeforeProviderAndPreventsDowngradeOrDuplicateGeneration() {
        long orderId = 900001L;
        var checkout = store.reserve(orderId, "owner", new BigDecimal("25.00"), "https://fixture.test/checkout/result");
        try {
            assertTrue(payments.findById(checkout.getPaymentId()).isPresent());
            assertThrows(ResponseStatusException.class, () -> store.reserve(orderId, "owner", new BigDecimal("25.00"), "https://fixture.test/checkout/result"));
            store.record(orderId, 0, "cs_fixture", "EXPIRED", null);
            assertEquals(1, store.renew(orderId, 0).getGeneration());
            assertEquals(1, store.renew(orderId, 0).getGeneration());
            assertThrows(ResponseStatusException.class, () -> store.record(orderId, 0, "cs_old", "OPEN", null));
            store.record(orderId, 1, "cs_new", "PAID", "pi_new");
            assertThrows(ResponseStatusException.class, () -> store.record(orderId, 1, "cs_new", "OPEN", "pi_new"));
            assertEquals("PAID", checkouts.findById(orderId).orElseThrow().getSessionState());
        } finally { checkouts.deleteById(orderId); payments.deleteById(checkout.getPaymentId()); }
    }
}
