package org.de013.notificationservice.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEmailMetrics {
    private final JdbcTemplate jdbc;

    public PaymentEmailMetrics(MeterRegistry registry, JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        for (String status : new String[] {"PENDING", "FAILED", "SENT"}) {
            Gauge.builder("ecommerce.notification.delivery", this, metrics -> metrics.deliveryCount(status))
                    .tag("status", status).register(registry);
        }
        Gauge.builder("ecommerce.notification.dlt.pending", this, PaymentEmailMetrics::pendingDltCount)
                .register(registry);
    }

    private double deliveryCount(String status) {
        return count("SELECT count(*) FROM payment_email_request WHERE delivery_status = ?", status);
    }

    private double pendingDltCount() {
        return count("SELECT count(*) FROM payment_email_dlt_outbox WHERE published_at IS NULL");
    }

    private double count(String sql, Object... args) {
        try {
            Long result = jdbc.queryForObject(sql, Long.class, args);
            return result == null ? Double.NaN : result.doubleValue();
        } catch (DataAccessException ex) {
            return Double.NaN;
        }
    }
}
