package org.de013.paymentservice.service;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CheckoutClosureService {
    private final PaymentOrderGuard guard;
    private final EntityManager entityManager;

    public record Result(long orderId, UUID operationId, String status, String reason) { }

    @Transactional
    public Result close(Long orderId, UUID operationId) {
        if (orderId == null || orderId <= 0 || operationId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid closure request");
        boolean held = guard.lock(orderId);
        Object[] row = (Object[]) entityManager.createNativeQuery(
                "SELECT close_operation_id, close_status, close_reason FROM payment_order_guard WHERE order_id = :id")
                .setParameter("id", orderId).getSingleResult();
        if (row[0] != null) {
            if (!operationId.equals(row[0]))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Order already has a closure operation");
            return new Result(orderId, operationId, (String) row[1], (String) row[2]);
        }
        boolean hasAttempts = (Boolean) entityManager.createNativeQuery(
                "SELECT EXISTS (SELECT 1 FROM payments WHERE order_id = :id)")
                .setParameter("id", orderId).getSingleResult();
        boolean safe = !held && !hasAttempts;
        String reason = safe ? null : held ? "RECONCILIATION_HOLD" : "PAYMENT_ATTEMPT_EXISTS";
        if (safe) {
            entityManager.createNativeQuery("UPDATE payment_order_guard SET close_operation_id = :op, close_status = 'SAFE', "
                    + "close_safe_proof = 'NO_PAYMENT_ATTEMPTS', close_safe_at = current_timestamp WHERE order_id = :id")
                    .setParameter("op", operationId).setParameter("id", orderId).executeUpdate();
        } else {
            entityManager.createNativeQuery("UPDATE payment_order_guard SET close_operation_id = :op, close_status = 'HOLD', "
                    + "close_reason = :reason WHERE order_id = :id")
                    .setParameter("op", operationId).setParameter("reason", reason).setParameter("id", orderId).executeUpdate();
        }
        return new Result(orderId, operationId, safe ? "SAFE" : "HOLD", reason);
    }

    @Transactional
    public void recordContradiction(Long orderId) {
        if (orderId == null || orderId <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid order");
        guard.lock(orderId);
        guard.recordSuccess(orderId);
    }
}
