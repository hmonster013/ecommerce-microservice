package org.de013.paymentservice.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxObservabilityTest {
    @Test void contextRoundTripAndIsolation() {
        var span = Span.wrap(SpanContext.create("0123456789abcdef0123456789abcdef", "0123456789abcdef", TraceFlags.getSampled(), TraceState.getDefault()));
        String parent;
        try (var scope = span.makeCurrent()) { parent = OutboxTrace.capture().get("traceparent"); }
        assertNotNull(parent);
        try (var scope = OutboxTrace.restore(parent, null).makeCurrent()) {
            assertEquals(span.getSpanContext().getTraceId(), Span.current().getSpanContext().getTraceId());
        }
        try (var scope = OutboxTrace.restore(null, null).makeCurrent()) { assertFalse(Span.current().getSpanContext().isValid()); }
        try (var scope = OutboxTrace.restore("malformed", null).makeCurrent()) { assertFalse(Span.current().getSpanContext().isValid()); }
        assertFalse(Span.current().getSpanContext().isValid());
    }

    @Test void gaugesReportValuesAndDatabaseFailure() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Double.class))).thenReturn(3.0);
        var registry = new SimpleMeterRegistry();
        new OutboxMetrics(jdbc, registry);
        assertEquals(3, registry.get("ecommerce.outbox.pending").tag("outbox", "financial").gauge().value());
        assertEquals(3, registry.get("ecommerce.outbox.oldest.age").tag("outbox", "notification").gauge().value());
        assertEquals(3, registry.get("ecommerce.outbox.reconciliation").tag("outbox", "financial").gauge().value());
        when(jdbc.queryForObject(anyString(), eq(Double.class))).thenThrow(new DataAccessResourceFailureException("private"));
        assertTrue(Double.isNaN(registry.get("ecommerce.outbox.pending").tag("outbox", "financial").gauge().value()));
    }
}
