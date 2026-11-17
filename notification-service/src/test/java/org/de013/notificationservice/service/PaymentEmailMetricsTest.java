package org.de013.notificationservice.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.ref.Reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class PaymentEmailMetricsTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void readsDeliveryAndDltStateAndReportsDatabaseErrors() {
        var dataSource = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var registry = new SimpleMeterRegistry();
        var metrics = new PaymentEmailMetrics(registry, jdbc);

        for (String status : new String[] {"PENDING", "FAILED", "SENT"}) {
            assertEquals(0, registry.get("ecommerce.notification.delivery").tag("status", status).gauge().value());
            Long id = jdbc.queryForObject("INSERT INTO notifications (channel, recipient, content) VALUES ('EMAIL', 'test', 'test') RETURNING id", Long.class);
            jdbc.update("""
                    INSERT INTO payment_email_request (event_id, channel, canonical_hash, payment_id, payment_number,
                        order_id, occurred_at, user_id, recipient, subject, content, notification_id, delivery_status)
                    VALUES (gen_random_uuid(), 'EMAIL', ?, 1, 'test', 1, now(), 'test', 'test', 'test', 'test', ?, ?)
                    """, "a".repeat(64), id, status);
        }
        for (String status : new String[] {"PENDING", "FAILED", "SENT"}) {
            assertEquals(1, registry.get("ecommerce.notification.delivery").tag("status", status).gauge().value());
        }
        var dlt = registry.get("ecommerce.notification.dlt.pending").gauge();
        assertEquals(0, dlt.value());
        jdbc.update("INSERT INTO payment_email_dlt_outbox (id, reason) VALUES (gen_random_uuid(), 'test')");
        assertEquals(1, dlt.value());
        jdbc.update("UPDATE payment_email_dlt_outbox SET published_at = now()");
        assertEquals(0, dlt.value());

        jdbc.execute("DROP TABLE payment_email_request CASCADE");
        jdbc.execute("DROP TABLE payment_email_dlt_outbox CASCADE");
        for (String status : new String[] {"PENDING", "FAILED", "SENT"}) {
            assertTrue(Double.isNaN(registry.get("ecommerce.notification.delivery").tag("status", status).gauge().value()));
        }
        assertTrue(Double.isNaN(dlt.value()));
        Reference.reachabilityFence(metrics);
    }
}
