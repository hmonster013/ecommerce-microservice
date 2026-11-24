package org.de013.paymentservice.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ReconciliationController {
    private final JdbcTemplate jdbc;

    public ReconciliationController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Attempt(long paymentId, String paymentNumber, String status, BigDecimal amount,
                          String currency, String gateway, boolean successfulChargeOrCapture) { }
    public record Snapshot(long orderId, Instant observedAt, boolean guardPresent, boolean reconciliationHold,
                           UUID closeOperationId, String closeStatus, String closeReason, Instant closeSafeAt,
                           boolean closeSafeProofPresent, List<Attempt> attempts) { }
    private record Guard(boolean hold, UUID operationId, String status, String reason, Instant safeAt, boolean proof) { }

    @GetMapping("/internal/reconciliation/orders/{orderId}")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Snapshot get(@PathVariable long orderId) {
        if (orderId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid order id");
        Instant observedAt = Instant.now();
        List<Guard> guards = jdbc.query("SELECT reconciliation_hold, close_operation_id, close_status, close_reason, close_safe_at, close_safe_proof IS NOT NULL FROM payment_order_guard WHERE order_id = ?",
                (rs, row) -> new Guard(rs.getBoolean(1), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
                        rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant(), rs.getBoolean(6)), orderId);
        List<Attempt> attempts = jdbc.query("SELECT p.id, p.payment_number, p.status, p.amount, p.currency, p.gateway_name, EXISTS (SELECT 1 FROM payment_transactions t WHERE t.payment_id = p.id AND t.status = 'SUCCEEDED' AND t.type IN ('CHARGE', 'CAPTURE')) FROM payments p WHERE p.order_id = ? ORDER BY p.id",
                (rs, row) -> new Attempt(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getString(5), rs.getString(6), rs.getBoolean(7)), orderId);
        Guard guard = guards.isEmpty() ? null : guards.get(0);
        return new Snapshot(orderId, observedAt, guard != null, guard != null && guard.hold(),
                guard == null ? null : guard.operationId(), guard == null ? null : guard.status(),
                guard == null ? null : guard.reason(), guard == null ? null : guard.safeAt(),
                guard != null && guard.proof(), attempts);
    }
}
