package org.de013.paymentservice.service;

import lombok.RequiredArgsConstructor;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@RequiredArgsConstructor
public class PaymentReservation {
    private final PaymentRepository payments;
    private final PaymentOrderGuard guard;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment reserve(Payment payment) {
        guard.requirePayable(payment.getOrderId());
        try {
            Payment saved = payments.saveAndFlush(payment);
            guard.financialChange(payment.getOrderId());
            return saved;
        } catch (DataIntegrityViolationException failure) {
            if (isOrderConflict(failure)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Order already has a blocking payment", failure);
            throw failure;
        }
    }

    public static boolean isOrderConflict(Throwable failure) {
        return hasConstraint(failure, "uq_payments_blocking_order");
    }

    public static boolean isHostedNumberConflict(Throwable failure) {
        return hasConstraint(failure, "payments_payment_number_key") || hasConstraint(failure, "idx_payment_number");
    }

    private static boolean hasConstraint(Throwable failure, String name) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && name.equals(violation.getConstraintName())) return true;
        }
        return false;
    }
}
