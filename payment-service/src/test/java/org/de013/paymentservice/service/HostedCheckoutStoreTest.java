package org.de013.paymentservice.service;

import org.de013.paymentservice.repository.HostedCheckoutRepository;
import org.de013.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = {"spring.cloud.bus.enabled=false", "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"})
@ActiveProfiles("test")
class HostedCheckoutStoreTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", DB::getJdbcUrl);
        p.add("spring.datasource.username", DB::getUsername);
        p.add("spring.datasource.password", DB::getPassword);
        p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
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
            assertEquals("cs_fixture", checkouts.findById(orderId).orElseThrow().getSessionId());
            assertThrows(ResponseStatusException.class, () -> store.record(orderId, 0, "cs_old", "OPEN", null));
            store.record(orderId, 0, "cs_fixture", "PAID", "pi_new");
            assertThrows(ResponseStatusException.class, () -> store.record(orderId, 0, "cs_fixture", "OPEN", "pi_new"));
            assertEquals("PAID", checkouts.findById(orderId).orElseThrow().getSessionState());
        } finally { checkouts.deleteById(orderId); payments.deleteById(checkout.getPaymentId()); }
    }
}
