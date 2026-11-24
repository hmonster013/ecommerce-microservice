package org.de013.orderservice.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.de013.orderservice.entity.enums.ReconciliationReason;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class OrderObservabilityTest {
    @Test void registersBoundedGaugesAndReportsDatabaseFailureAsNan() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(3L).thenThrow(new DataAccessResourceFailureException("unavailable"));
        var registry = new SimpleMeterRegistry();
        new OrderObservability(registry, jdbc);

        assertEquals(5, registry.find("ecommerce.saga.orders").gauges().size());
        assertEquals(ReconciliationReason.values().length, registry.find("ecommerce.reconciliation.reasons").gauges().size());
        var gauge = registry.get("ecommerce.saga.orders").tag("phase", "ALLOCATING").gauge();
        assertEquals(3.0, gauge.value());
        assertTrue(Double.isNaN(gauge.value()));
        assertNotNull(registry.find("ecommerce.saga.failures").gauge());
        assertNotNull(registry.find("ecommerce.reconciliation.findings").gauge());
    }
}
