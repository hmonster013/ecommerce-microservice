package org.de013.paymentservice.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@RequiredArgsConstructor
public class PaymentOrderGuard {
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lock(Long orderId) {
        entityManager.createNativeQuery("INSERT INTO payment_order_guard (order_id) VALUES (:id) ON CONFLICT (order_id) DO NOTHING")
                .setParameter("id", orderId).executeUpdate();
        return (Boolean) entityManager.createNativeQuery("SELECT reconciliation_hold OR close_operation_id IS NOT NULL OR permanent_fence FROM payment_order_guard WHERE order_id = :id FOR UPDATE")
                .setParameter("id", orderId).getSingleResult();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSuccess(Long orderId) {
        int changed = entityManager.createNativeQuery("UPDATE payment_order_guard SET close_status = 'HOLD', close_reason = 'LATE_SUCCESS' "
                + "WHERE order_id = :id AND close_status = 'SAFE'")
                .setParameter("id", orderId).executeUpdate();
        if (changed != 0) financialChange(orderId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requirePayable(Long orderId) {
        if (lock(orderId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Order requires payment reconciliation");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void hold(Long orderId) {
        lock(orderId);
        entityManager.createNativeQuery("UPDATE payment_order_guard SET reconciliation_hold = true WHERE order_id = :id")
                .setParameter("id", orderId).executeUpdate();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void financialChange(Long orderId) {
        entityManager.createNativeQuery("UPDATE payment_order_guard SET financial_version = financial_version + 1 WHERE order_id = :id")
                .setParameter("id", orderId).executeUpdate();
        entityManager.createNativeQuery("INSERT INTO compensation_history(operation_id,status,reason,financial_version) SELECT o.operation_id,'HOLD','FINANCIAL_FACTS_CHANGED',g.financial_version FROM compensation_operation o JOIN payment_order_guard g ON g.order_id=o.order_id WHERE o.order_id=:id AND o.status='COMPENSATED'")
                .setParameter("id", orderId).executeUpdate();
        entityManager.createNativeQuery("UPDATE compensation_operation SET status='HOLD',reason='FINANCIAL_FACTS_CHANGED',claim_token=NULL,claim_until=NULL WHERE order_id=:id AND status='COMPENSATED'")
                .setParameter("id", orderId).executeUpdate();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void permanentFence(Long orderId) {
        lock(orderId);
        entityManager.createNativeQuery("UPDATE payment_order_guard SET permanent_fence = true WHERE order_id = :id")
                .setParameter("id", orderId).executeUpdate();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireNotPermanentlyFenced(Long orderId) {
        if (Boolean.TRUE.equals(entityManager.createNativeQuery("SELECT permanent_fence FROM payment_order_guard WHERE order_id = :id")
                .setParameter("id", orderId).getSingleResult()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order requires payment reconciliation");
    }
}
