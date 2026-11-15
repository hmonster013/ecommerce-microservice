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
        return (Boolean) entityManager.createNativeQuery("SELECT reconciliation_hold FROM payment_order_guard WHERE order_id = :id FOR UPDATE")
                .setParameter("id", orderId).getSingleResult();
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
}
