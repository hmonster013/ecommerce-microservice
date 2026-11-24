package org.de013.paymentservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CompensationProofStore {
    private static final String UNRESOLVED = "EXISTS (SELECT 1 FROM stripe_financial_observation WHERE order_id IS NULL)";
    private final JdbcTemplate jdbc;
    private final PaymentOrderGuard guard;
    private final ObjectMapper mapper;

    public record Request(UUID operationId, long findingRevision, long expectedPaymentId, String expectedPaymentNumber,
                          BigDecimal expectedAmount, String expectedCurrency, UUID expectedCloseOperationId) { }
    public record View(long orderId, UUID operationId, long findingRevision, String status, String reason, UUID proofId,
                       long financialVersion, Long proofVersion, Instant observedAt, Long paymentId, String paymentNumber,
                       BigDecimal amount, String currency, UUID closeOperationId, JsonNode evidence) { }
    public record Claim(View view, UUID token, String snapshot) { }

    @Transactional
    public Claim begin(long orderId, Request r) {
        validate(orderId, r);
        guard.lock(orderId);
        var rows = jdbc.queryForList("SELECT * FROM compensation_operation WHERE order_id=?", orderId);
        if (!rows.isEmpty()) {
            var row = rows.get(0);
            if (!r.operationId().equals(row.get("operation_id")) || r.findingRevision() != ((Number) row.get("finding_revision")).longValue()
                    || r.expectedPaymentId() != ((Number) row.get("expected_payment_id")).longValue()
                    || !r.expectedPaymentNumber().equals(row.get("expected_payment_number"))
                    || r.expectedAmount().compareTo((BigDecimal) row.get("expected_amount")) != 0
                    || !r.expectedCurrency().equals(row.get("expected_currency"))
                    || !java.util.Objects.equals(r.expectedCloseOperationId(), row.get("expected_close_operation_id")))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Compensation operation mismatch");
            if (!"VERIFYING".equals(row.get("status"))) return new Claim(view(orderId, r.operationId()), null, null);
            Boolean active = jdbc.queryForObject("SELECT claim_until > clock_timestamp() FROM compensation_operation WHERE order_id=?", Boolean.class, orderId);
            if (Boolean.TRUE.equals(active)) return new Claim(view(orderId, r.operationId()), null, null);
            UUID token = UUID.randomUUID();
            String snapshot = snapshot(orderId);
            jdbc.update("UPDATE compensation_operation SET claim_token=?, claim_until=clock_timestamp()+interval '30 seconds',snapshot_version=(SELECT financial_version FROM payment_order_guard WHERE order_id=?),snapshot=CAST(? AS jsonb) WHERE order_id=?", token, orderId, snapshot, orderId);
            return new Claim(view(orderId, r.operationId()), token, snapshot);
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT permanent_fence FROM payment_order_guard WHERE order_id=?", Boolean.class, orderId)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order already fenced");
        guard.permanentFence(orderId);
        UUID token = UUID.randomUUID();
        String snapshot = snapshot(orderId);
        jdbc.update("INSERT INTO compensation_operation(order_id,operation_id,finding_revision,expected_payment_id,expected_payment_number,expected_amount,expected_currency,expected_close_operation_id,status,claim_token,claim_until,snapshot_version,snapshot) VALUES (?,?,?,?,?,?,?,?, 'VERIFYING',?,clock_timestamp()+interval '30 seconds',(SELECT financial_version FROM payment_order_guard WHERE order_id=?),CAST(? AS jsonb))",
                orderId, r.operationId(), r.findingRevision(), r.expectedPaymentId(), r.expectedPaymentNumber(), r.expectedAmount(), r.expectedCurrency(), r.expectedCloseOperationId(), token, orderId, snapshot);
        history(orderId, "VERIFYING", null);
        return new Claim(view(orderId, r.operationId()), token, snapshot);
    }

    private String snapshot(long orderId) {
        return jdbc.queryForObject("SELECT jsonb_build_object('guard',(SELECT jsonb_build_array(g.reconciliation_hold,g.permanent_fence,g.close_operation_id,g.close_status,g.close_reason) FROM payment_order_guard g WHERE g.order_id=?),'payments',COALESCE((SELECT jsonb_agg(jsonb_build_array(p.id,p.order_id,p.payment_number,p.amount,p.currency,p.gateway_name,p.status,p.stripe_payment_intent_id,p.stripe_intent_livemode,p.stripe_intent_created_by_service,p.blocking,p.verified_release) ORDER BY p.id) FROM payments p WHERE p.order_id=?),'[]'::jsonb),'transactions',COALESCE((SELECT jsonb_agg(jsonb_build_array(t.id,t.payment_id,t.type,t.status,t.amount,t.currency,t.stripe_charge_id,t.stripe_payment_intent_id) ORDER BY t.id) FROM payment_transactions t JOIN payments p ON p.id=t.payment_id WHERE p.order_id=?),'[]'::jsonb),'refunds',COALESCE((SELECT jsonb_agg(jsonb_build_array(r.id,r.payment_id,r.order_id,r.amount,r.currency,r.status,r.stripe_refund_id,r.stripe_charge_id,r.stripe_payment_intent_id) ORDER BY r.id) FROM refunds r JOIN payments p ON p.id=r.payment_id WHERE p.order_id=?),'[]'::jsonb),'hosted',COALESCE((SELECT jsonb_agg(jsonb_build_array(h.order_id,h.payment_id,h.generation,h.session_id,h.session_state) ORDER BY h.order_id) FROM hosted_checkouts h WHERE h.order_id=?),'[]'::jsonb),'retry',COALESCE((SELECT jsonb_agg(jsonb_build_array(r.id,r.payment_id,r.state) ORDER BY r.id) FROM payment_retry_attempt r JOIN payments p ON p.id=r.payment_id WHERE p.order_id=?),'[]'::jsonb))::text", String.class, orderId, orderId, orderId, orderId, orderId, orderId);
    }

    private void validate(long orderId, Request r) {
        if (orderId <= 0 || r == null || r.operationId() == null || r.findingRevision() <= 0 || r.expectedPaymentId() <= 0
                || r.expectedPaymentNumber() == null || r.expectedPaymentNumber().isBlank() || r.expectedPaymentNumber().length() > 100
                || r.expectedAmount() == null || r.expectedAmount().signum() <= 0 || r.expectedAmount().scale() > 2
                || r.expectedAmount().precision() > 19 || r.expectedCurrency() == null || !r.expectedCurrency().matches("[A-Z]{3}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid compensation request");
    }

    @Transactional
    public View complete(long orderId, UUID operationId, UUID token, StripeCompensationVerifier.Result result) {
        if (orderId <= 0 || operationId == null || token == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid claim");
        guard.lock(orderId);
        var rows = jdbc.queryForList("SELECT o.*,g.financial_version,g.close_operation_id,g.reconciliation_hold, o.claim_until > clock_timestamp() AS active FROM compensation_operation o JOIN payment_order_guard g ON g.order_id=o.order_id WHERE o.order_id=? AND o.operation_id=?", orderId, operationId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Compensation operation not found");
        var row = rows.get(0);
        if (!"VERIFYING".equals(row.get("status")) || !token.equals(row.get("claim_token")) || !Boolean.TRUE.equals(row.get("active")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Compensation claim is stale");
        String reason = null;
        long version = ((Number) row.get("financial_version")).longValue();
        if (version != ((Number) row.get("snapshot_version")).longValue()
                || !Boolean.TRUE.equals(jdbc.queryForObject("SELECT snapshot=CAST(? AS jsonb) FROM compensation_operation WHERE order_id=?", Boolean.class, snapshot(orderId), orderId))) reason = "FINANCIAL_FACTS_CHANGED";
        else if (!java.util.Objects.equals(row.get("expected_close_operation_id"), row.get("close_operation_id"))) reason = "CLOSURE_MISMATCH";
        else if (result == null || !result.safe() || result.evidence() == null) reason = "PROVIDER_UNVERIFIED";
        else if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT " + UNRESOLVED, Boolean.class))) reason = "UNRESOLVED_FINANCIAL_OBSERVATION";
        else if (!matchesCandidate(orderId, row, result.evidence())) reason = "CANDIDATE_MISMATCH";
        if (reason != null) {
            jdbc.update("UPDATE compensation_operation SET status='HOLD',reason=?,claim_token=NULL,claim_until=NULL WHERE order_id=?", reason, orderId);
            history(orderId, "HOLD", reason);
            return view(orderId, operationId);
        }
        UUID proof = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO compensation_proof(proof_id,operation_id,proof_version,observed_at,evidence) VALUES (?,?,?, ?,CAST(? AS jsonb))",
                    proof, operationId, version, java.sql.Timestamp.from(result.evidence().observedAt()), mapper.writeValueAsString(result.evidence()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Cannot serialize evidence", e); }
        jdbc.update("UPDATE compensation_operation SET status='COMPENSATED',reason=NULL,proof_id=?,claim_token=NULL,claim_until=NULL WHERE order_id=?", proof, orderId);
        history(orderId, "COMPENSATED", null);
        return view(orderId, operationId);
    }

    private boolean matchesCandidate(long orderId, java.util.Map<String, Object> row, StripeCompensationVerifier.Evidence evidence) {
        if (evidence.observedAt() == null || evidence.observedAt().isAfter(Instant.now()) || evidence.accountId() == null || evidence.charges() == null) return false;
        var success = evidence.charges().stream().filter(c -> "succeeded".equals(c.status())).toList();
        if (success.size() != 1) return false;
        var charge = success.get(0);
        if (charge.intentId() == null || charge.id() == null || charge.refunds() == null || charge.refunds().isEmpty()) return false;
        long amount;
        try { amount = ((BigDecimal) row.get("expected_amount")).movePointRight(2).longValueExact(); }
        catch (ArithmeticException e) { return false; }
        if (!"USD".equals(row.get("expected_currency")) || charge.amount() != amount) return false;
        long sum = 0;
        try {
            for (var refund : charge.refunds()) {
                if (refund == null || refund.id() == null || refund.id().isBlank() || refund.amount() <= 0 || !"succeeded".equals(refund.status())) return false;
                sum = Math.addExact(sum, refund.amount());
            }
        } catch (ArithmeticException e) { return false; }
        if (sum != amount) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM payments WHERE order_id=? AND id=? AND payment_number=? AND amount=? AND currency=? AND stripe_payment_intent_id=? AND stripe_intent_livemode=? AND stripe_intent_created_by_service=true AND gateway_name='STRIPE')", Boolean.class,
                orderId, row.get("expected_payment_id"), row.get("expected_payment_number"), row.get("expected_amount"), row.get("expected_currency"), charge.intentId(), evidence.livemode()));
    }

    private void history(long orderId, String state, String reason) {
        jdbc.update("INSERT INTO compensation_history(operation_id,status,reason,financial_version) SELECT o.operation_id,?,?,g.financial_version FROM compensation_operation o JOIN payment_order_guard g ON g.order_id=o.order_id WHERE o.order_id=?", state, reason, orderId);
    }

    @Transactional(readOnly = true)
    public View view(long orderId, UUID operationId) {
        var rows = jdbc.query("SELECT o.order_id,o.operation_id,o.finding_revision,o.status,o.reason,o.proof_id,g.financial_version,p.proof_version,p.observed_at,o.expected_payment_id,o.expected_payment_number,o.expected_amount,o.expected_currency,o.expected_close_operation_id,p.evidence::text," + UNRESOLVED + " FROM compensation_operation o JOIN payment_order_guard g ON g.order_id=o.order_id LEFT JOIN compensation_proof p ON p.proof_id=o.proof_id WHERE o.order_id=? AND o.operation_id=?", (rs, n) -> {
            try {
                var time = rs.getTimestamp(9);
                String evidence = rs.getString(15);
                boolean unresolved = "COMPENSATED".equals(rs.getString(4)) && rs.getBoolean(16);
                return new View(rs.getLong(1), rs.getObject(2, UUID.class), rs.getLong(3), unresolved ? "HOLD" : rs.getString(4), unresolved ? "UNRESOLVED_FINANCIAL_OBSERVATION" : rs.getString(5),
                        rs.getObject(6, UUID.class), rs.getLong(7), rs.getObject(8, Long.class), time == null ? null : time.toInstant(),
                        rs.getObject(10, Long.class), rs.getString(11), rs.getBigDecimal(12), rs.getString(13), rs.getObject(14, UUID.class),
                        evidence == null ? null : mapper.readTree(evidence));
            } catch (java.io.IOException e) { throw new IllegalStateException("Invalid stored evidence", e); }
        }, orderId, operationId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Compensation operation not found");
        return rows.get(0);
    }
}
