package org.de013.orderservice.service.impl;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.de013.orderservice.entity.enums.ReconciliationReason;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderObservability {
    private final JdbcTemplate jdbc;

    public OrderObservability(MeterRegistry registry, JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        for (String phase : new String[] {"ALLOCATING", "COMPENSATING", "FULFILL_PENDING", "CLOSING", "RELEASE_PENDING"}) {
            Gauge.builder("ecommerce.saga.orders", this, metrics -> metrics.count(
                    "SELECT count(*) FROM orders WHERE allocation_phase = ?", phase))
                    .tag("phase", phase).register(registry);
        }
        Gauge.builder("ecommerce.saga.failures", this, metrics -> metrics.count(
                "SELECT count(*) FROM orders WHERE allocation_phase = 'FAILED'"))
                .register(registry);
        Gauge.builder("ecommerce.reconciliation.findings", this, metrics -> metrics.count(
                "SELECT count(*) FROM reconciliation_finding"))
                .register(registry);
        for (ReconciliationReason reason : ReconciliationReason.values()) {
            Gauge.builder("ecommerce.reconciliation.reasons", this, metrics -> metrics.count(
                    "SELECT count(*) FROM reconciliation_finding f JOIN reconciliation_revision r ON r.order_id = f.order_id AND r.revision = f.current_revision WHERE r.reasons @> jsonb_build_array(CAST(? AS text))", reason.name()))
                    .tag("reason", reason.name()).register(registry);
        }
    }

    private double count(String sql, Object... args) {
        try {
            Long count = jdbc.queryForObject(sql, Long.class, args);
            return count == null ? Double.NaN : count.doubleValue();
        } catch (DataAccessException e) {
            return Double.NaN;
        }
    }
}
