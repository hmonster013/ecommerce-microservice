package org.de013.paymentservice.service;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.stripe.exception.StripeException;
import org.de013.paymentservice.gateway.stripe.StripeClosureVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class CheckoutClosureService {
    private final PaymentOrderGuard guard;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;
    private final StripeClosureVerifier verifier;
    private final ObjectMapper objectMapper;

    public record Result(long orderId, UUID operationId, String status, String reason) { }
    record Attempt(Long id, String intent, Boolean mode, boolean trusted, BigDecimal amount, String currency, String status, boolean hosted, String gateway, boolean successHistory, String gatewayRef, String method) { }
    record Processing(Result result, UUID claim, List<Attempt> attempts) { }
    record Proof(UUID operationId, List<StripeClosureVerifier.Observation> attempts) { }

    private Long minorAmount(Attempt a) {
        if (!a.trusted() || a.hosted() || a.successHistory() || (a.gateway() != null && !"STRIPE".equalsIgnoreCase(a.gateway()))
                || "SUCCEEDED".equals(a.status()) || a.intent() == null || a.intent().isBlank() || a.mode() == null
                || a.amount() == null || a.currency() == null || a.currency().isBlank()) return null;
        try {
            long amount = (List.of("JPY", "KRW", "VND").contains(a.currency()) ? a.amount() : a.amount().movePointRight(2)).longValueExact();
            return amount > 0 ? amount : null;
        } catch (ArithmeticException e) { return null; }
    }

    private void renew(Long orderId, UUID operationId, UUID claim) {
        boolean renewed = Boolean.TRUE.equals(newTransaction().execute(tx -> {
            guard.lock(orderId);
            return entityManager.createNativeQuery("UPDATE payment_order_guard SET close_claim_until = clock_timestamp() + interval '30 seconds' WHERE order_id = :id AND close_operation_id = :op AND close_status = 'CLOSING' AND close_claim_token = :claim AND close_claim_until > clock_timestamp()")
                    .setParameter("id", orderId).setParameter("op", operationId).setParameter("claim", claim).executeUpdate() == 1;
        }));
        if (!renewed) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Closure claim expired");
    }

    private List<Attempt> attempts(Long orderId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("SELECT p.id, p.stripe_payment_intent_id, p.stripe_intent_livemode, p.stripe_intent_created_by_service, p.amount, p.currency, p.status, EXISTS (SELECT 1 FROM hosted_checkouts h WHERE h.payment_id = p.id), p.gateway_name, EXISTS (SELECT 1 FROM payment_transactions t WHERE t.payment_id = p.id AND t.status = 'SUCCEEDED' AND t.type IN ('CHARGE', 'CAPTURE')), p.gateway_txn_ref, p.method FROM payments p WHERE p.order_id = :id ORDER BY p.id")
                .setParameter("id", orderId).getResultList();
        return rows.stream().map(r -> new Attempt(((Number) r[0]).longValue(), (String) r[1], (Boolean) r[2], Boolean.TRUE.equals(r[3]), (BigDecimal) r[4], (String) r[5], (String) r[6], Boolean.TRUE.equals(r[7]), (String) r[8], Boolean.TRUE.equals(r[9]), (String) r[10], (String) r[11])).toList();
    }

    private TransactionTemplate newTransaction() {
        var tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }

    Processing beginProcessing(Long orderId, UUID operationId) {
        if (orderId == null || orderId <= 0 || operationId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid closure request");
        return newTransaction().execute(status -> {
            guard.lock(orderId);
            Object[] row = (Object[]) entityManager.createNativeQuery("SELECT close_operation_id, close_status, close_reason, "
                    + "reconciliation_hold, close_claim_until > clock_timestamp() FROM payment_order_guard WHERE order_id = :id")
                    .setParameter("id", orderId).getSingleResult();
            if (row[0] != null && !operationId.equals(row[0]))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Order already has a closure operation");
            if (row[0] != null && !"CLOSING".equals(row[1]))
                return new Processing(new Result(orderId, operationId, (String) row[1], (String) row[2]), null, List.of());
            if (Boolean.TRUE.equals(row[4]))
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Closure in progress");
            if (Boolean.TRUE.equals(row[3])) {
                entityManager.createNativeQuery("UPDATE payment_order_guard SET close_operation_id = :op, close_status = 'HOLD', close_reason = 'RECONCILIATION_HOLD', close_claim_token = NULL, close_claim_until = NULL WHERE order_id = :id")
                        .setParameter("op", operationId).setParameter("id", orderId).executeUpdate();
                guard.financialChange(orderId);
                return new Processing(new Result(orderId, operationId, "HOLD", "RECONCILIATION_HOLD"), null, List.of());
            }
            if (row[0] == null) {
                boolean attempts = (Boolean) entityManager.createNativeQuery("SELECT EXISTS (SELECT 1 FROM payments WHERE order_id = :id)")
                        .setParameter("id", orderId).getSingleResult();
                if (Boolean.TRUE.equals(row[3]) || !attempts) {
                    boolean safe = !Boolean.TRUE.equals(row[3]);
                    String reason = safe ? null : "RECONCILIATION_HOLD";
                    if (safe) entityManager.createNativeQuery("UPDATE payment_order_guard SET close_operation_id = :op, close_status = 'SAFE', close_safe_proof = 'NO_PAYMENT_ATTEMPTS', close_safe_at = current_timestamp WHERE order_id = :id")
                            .setParameter("op", operationId).setParameter("id", orderId).executeUpdate();
                    else entityManager.createNativeQuery("UPDATE payment_order_guard SET close_operation_id = :op, close_status = 'HOLD', close_reason = 'RECONCILIATION_HOLD' WHERE order_id = :id")
                            .setParameter("op", operationId).setParameter("id", orderId).executeUpdate();
                    guard.financialChange(orderId);
                    return new Processing(new Result(orderId, operationId, safe ? "SAFE" : "HOLD", reason), null, List.of());
                }
            }
            UUID claim = UUID.randomUUID();
            entityManager.createNativeQuery("UPDATE payment_order_guard SET close_operation_id = :op, close_status = 'CLOSING', close_claim_token = :claim, close_claim_until = clock_timestamp() + interval '30 seconds' WHERE order_id = :id")
                    .setParameter("op", operationId).setParameter("claim", claim).setParameter("id", orderId).executeUpdate();
            if (row[0] == null) guard.financialChange(orderId);
            return new Processing(null, claim, attempts(orderId));
        });
    }

    boolean completeProcessing(Long orderId, UUID operationId, UUID claim, String status, String reason) {
        if (orderId == null || orderId <= 0 || operationId == null || claim == null || !"HOLD".equals(status))
            throw new IllegalArgumentException("Only HOLD completion is supported without provider proof");
        return Boolean.TRUE.equals(newTransaction().execute(tx -> {
            guard.lock(orderId);
            int changed = entityManager.createNativeQuery("UPDATE payment_order_guard SET close_status = 'HOLD', close_reason = :reason, close_claim_token = NULL, close_claim_until = NULL "
                    + "WHERE order_id = :id AND close_operation_id = :op AND close_status = 'CLOSING' AND close_claim_token = :claim AND close_claim_until > clock_timestamp()")
                    .setParameter("id", orderId).setParameter("op", operationId).setParameter("claim", claim).setParameter("reason", reason).executeUpdate();
            if (changed == 1) guard.financialChange(orderId);
            return changed == 1;
        }));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public Result close(Long orderId, UUID operationId) {
        Processing processing = beginProcessing(orderId, operationId);
        if (processing.result() != null) return processing.result();
        String reason = processing.attempts().stream().anyMatch(a -> minorAmount(a) == null) ? "PAYMENT_ATTEMPT_EXISTS" : null;
        List<StripeClosureVerifier.Observation> observations = new ArrayList<>();
        try {
            for (Attempt a : reason == null ? processing.attempts() : List.<Attempt>of()) {
                renew(orderId, operationId, processing.claim());
                var evidence = verifier.verify(a.intent(), a.mode(), minorAmount(a), a.currency(), operationId, a.id());
                if (!evidence.safe()) { reason = evidence.reason(); break; }
                observations.add(evidence.observation());
            }
        } catch (StripeException e) {
            newTransaction().executeWithoutResult(tx -> {
                guard.lock(orderId);
                entityManager.createNativeQuery("UPDATE payment_order_guard SET close_claim_until = clock_timestamp() WHERE order_id = :id AND close_operation_id = :op AND close_status = 'CLOSING' AND close_claim_token = :claim")
                        .setParameter("id", orderId).setParameter("op", operationId).setParameter("claim", processing.claim()).executeUpdate();
            });
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Provider verification unavailable");
        }
        String outcome = reason;
        Result result = newTransaction().execute(tx -> {
            guard.lock(orderId);
            Object[] row = (Object[]) entityManager.createNativeQuery("SELECT reconciliation_hold, close_operation_id, close_status, close_claim_token, close_claim_until > clock_timestamp() FROM payment_order_guard WHERE order_id = :id")
                    .setParameter("id", orderId).getSingleResult();
            if (!operationId.equals(row[1]) || !"CLOSING".equals(row[2]) || !processing.claim().equals(row[3]) || !Boolean.TRUE.equals(row[4]))
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Closure claim expired");
            String hold = Boolean.TRUE.equals(row[0]) ? "RECONCILIATION_HOLD" : !processing.attempts().equals(attempts(orderId)) ? "PAYMENT_ATTEMPT_CHANGED" : outcome;
            if (hold != null) {
                entityManager.createNativeQuery("UPDATE payment_order_guard SET close_status = 'HOLD', close_reason = :reason, close_claim_token = NULL, close_claim_until = NULL WHERE order_id = :id")
                        .setParameter("reason", hold).setParameter("id", orderId).executeUpdate();
                guard.financialChange(orderId);
                return new Result(orderId, operationId, "HOLD", hold);
            }
            String proof;
            try { proof = objectMapper.writeValueAsString(new Proof(operationId, List.copyOf(observations))); }
            catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize closure proof", e); }
            entityManager.createNativeQuery("UPDATE payment_order_guard SET close_status = 'SAFE', close_safe_proof = :proof, close_safe_at = current_timestamp, close_claim_token = NULL, close_claim_until = NULL WHERE order_id = :id")
                    .setParameter("proof", proof).setParameter("id", orderId).executeUpdate();
            guard.financialChange(orderId);
            return new Result(orderId, operationId, "SAFE", null);
        });
        return result;
    }

    @Transactional
    public void recordContradiction(Long orderId) {
        if (orderId == null || orderId <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid order");
        guard.lock(orderId);
        guard.recordSuccess(orderId);
    }
}
