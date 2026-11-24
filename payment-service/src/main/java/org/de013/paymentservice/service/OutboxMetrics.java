package org.de013.paymentservice.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class OutboxMetrics {
    private static final Logger log = LoggerFactory.getLogger(OutboxMetrics.class);
    private final JdbcTemplate jdbc;
    private final java.util.concurrent.atomic.AtomicBoolean failureLogged = new java.util.concurrent.atomic.AtomicBoolean();

    OutboxMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        register(registry, "financial", "payment_outbox");
        register(registry, "notification", "payment_notification_outbox");
    }

    private void register(MeterRegistry registry, String tag, String table) {
        Gauge.builder("ecommerce.outbox.pending", this, ignored -> query("select count(*) from " + table + " where state='PENDING'"))
                .tag("outbox", tag).register(registry);
        Gauge.builder("ecommerce.outbox.reconciliation", this, ignored -> query("select count(*) from " + table + " where state='RECONCILIATION'"))
                .tag("outbox", tag).register(registry);
        Gauge.builder("ecommerce.outbox.oldest.age", this, ignored -> query("select coalesce(greatest(0, extract(epoch from (clock_timestamp() - min(created_at)))), 0) from " + table + " where state='PENDING'"))
                .tag("outbox", tag).baseUnit("seconds").register(registry);
    }

    private double query(String sql) {
        try {
            Double value = jdbc.queryForObject(sql, Double.class);
            return value == null ? Double.NaN : value;
        } catch (DataAccessException e) {
            if (failureLogged.compareAndSet(false, true)) log.warn("Outbox metric query failed");
            return Double.NaN;
        }
    }
}
