package org.de013.orderservice.service.impl;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
class ReconciliationStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    ReconciliationStore(JdbcTemplate jdbc, TransactionTemplate tx) { this.jdbc = jdbc; this.tx = tx; }

    record Claim(long orderId, UUID token) { }

    List<Claim> claim(int limit, Duration lease) {
        if (limit <= 0 || lease.isZero() || lease.isNegative() || lease.toMillis() <= 0) throw new IllegalArgumentException();
        return tx.execute(s -> jdbc.query("""
                WITH due AS (
                    SELECT order_id FROM reconciliation_finding
                    WHERE next_check_at <= clock_timestamp() AND (claim_until IS NULL OR claim_until <= clock_timestamp())
                    ORDER BY next_check_at, order_id FOR UPDATE SKIP LOCKED LIMIT ?
                )
                UPDATE reconciliation_finding f SET claim_token = gen_random_uuid(),
                    claim_until = clock_timestamp() + (? * interval '1 millisecond')
                FROM due WHERE f.order_id = due.order_id RETURNING f.order_id, f.claim_token
                """, (rs, n) -> new Claim(rs.getLong(1), rs.getObject(2, UUID.class)), limit, lease.toMillis()));
    }

    // Caller may join this transaction after locking and validating the local Order and intent snapshot.
    boolean complete(Claim claim, String evidenceJson, String reasonsJson, Duration delay) {
        if (delay.isNegative()) throw new IllegalArgumentException();
        return Boolean.TRUE.equals(tx.execute(s -> {
            var revisions = jdbc.query("""
                    UPDATE reconciliation_finding SET current_revision = current_revision + 1,
                        next_check_at = clock_timestamp() + (? * interval '1 millisecond'), claim_token = NULL, claim_until = NULL
                    WHERE order_id = ? AND claim_token = ? AND claim_until > clock_timestamp()
                    RETURNING current_revision
                    """, (rs, n) -> rs.getLong(1), delay.toMillis(), claim.orderId(), claim.token());
            if (revisions.isEmpty()) return false;
            jdbc.update("INSERT INTO reconciliation_revision(order_id, revision, evidence, reasons) VALUES (?, ?, CAST(? AS jsonb), CAST(? AS jsonb))",
                    claim.orderId(), revisions.get(0), evidenceJson, reasonsJson);
            return true;
        }));
    }
}
