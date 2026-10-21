package org.de013.paymentservice.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.de013.paymentservice.entity.*;
import org.de013.paymentservice.entity.enums.*;
import org.de013.paymentservice.repository.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class HostedCheckoutStore {
    private final EntityManager entityManager;
    private final HostedCheckoutRepository checkouts;
    private final PaymentRepository payments;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public HostedCheckout reserve(Long orderId, String userId, BigDecimal amount, String returnUrl) {
        if (!payments.findByOrderId(orderId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order already has a payment; reconcile it first");
        }
        var payment = payments.saveAndFlush(Payment.builder().orderId(orderId).userId(userId)
                .paymentNumber("HOSTED-" + orderId).amount(amount).currency(Currency.USD)
                .method(PaymentMethodType.CARD).status(PaymentStatus.PENDING).gatewayName("STRIPE")
                .createdBy(userId).updatedBy(userId).build());
        var checkout = new HostedCheckout();
        checkout.setOrderId(orderId);
        checkout.setUserId(userId);
        checkout.setPaymentId(payment.getId());
        checkout.setAmount(amount);
        checkout.setSuccessUrl(returnUrl + "?orderId=" + orderId);
        checkout.setCancelUrl(returnUrl + "?orderId=" + orderId);
        checkout.setReservedAt(Instant.now());
        // Insert only: an assigned-ID merge must never overwrite a concurrent owner's reservation.
        entityManager.persist(checkout);
        entityManager.flush();
        return checkout;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public HostedCheckout renew(Long orderId, int expectedGeneration) {
        var checkout = checkouts.lockByOrderId(orderId).orElseThrow();
        if (checkout.getGeneration() == expectedGeneration && "EXPIRED".equals(checkout.getSessionState())) {
            checkout.setGeneration(expectedGeneration + 1);
            checkout.setSessionId(null);
            checkout.setSessionState("UNKNOWN");
            checkout.setReservedAt(Instant.now());
        }
        return checkout;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long orderId, int generation, String sessionId, String state, String intent) {
        var checkout = checkouts.lockByOrderId(orderId).orElseThrow();
        if (checkout.getGeneration() != generation) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout session changed; read again");
        }
        if (checkout.getSessionId() != null && !checkout.getSessionId().equals(sessionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout session mismatch");
        }
        checkout.setSessionId(sessionId);
        if ("PAID".equals(checkout.getSessionState()) && !"PAID".equals(state)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A stale response cannot downgrade a paid checkout");
        }
        checkout.setSessionState(state);
        var payment = payments.findById(checkout.getPaymentId()).orElseThrow();
        payment.setStripePaymentIntentId(intent);
        payment.setGatewayTxnRef(sessionId);
        payment.setStatus("PAID".equals(state) ? PaymentStatus.SUCCEEDED : PaymentStatus.PENDING);
    }
}
