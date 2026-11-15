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
import org.de013.paymentservice.client.HostedOrderClient;
import org.de013.paymentservice.gateway.stripe.StripeHostedGateway;
import com.stripe.exception.StripeException;

@Repository
@RequiredArgsConstructor
public class HostedCheckoutStore {
    private final EntityManager entityManager;
    private final HostedCheckoutRepository checkouts;
    private final PaymentRepository payments;
    private final PaymentOrderGuard guard;
    private final HostedOrderClient orders;
    private final StripeHostedGateway stripe;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public HostedCheckout reserve(Long orderId, String userId, BigDecimal amount, String returnUrl) {
        guard.requirePayable(orderId);
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
    public void record(Long orderId, int generation, String sessionId, String state, String intent) {
        guard.lock(orderId);
        var existing = checkouts.findById(orderId).orElseThrow();
        var payment = payments.findById(existing.getPaymentId()).orElseThrow();
        var checkout = checkouts.lockByOrderId(orderId).orElseThrow();
        if (checkout.getGeneration() != generation) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout session changed; read again");
        }
        if (checkout.getSessionId() != null && !checkout.getSessionId().equals(sessionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout session mismatch");
        }
        if (intent != null && payment.getStripePaymentIntentId() != null && !intent.equals(payment.getStripePaymentIntentId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout payment intent mismatch");
        }
        if (payment.getGatewayTxnRef() != null && !payment.getGatewayTxnRef().equals(sessionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout payment session mismatch");
        }
        checkout.setSessionId(sessionId);
        if ("PAID".equals(checkout.getSessionState()) && !"PAID".equals(state)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A stale response cannot downgrade a paid checkout");
        }
        checkout.setSessionState(state);
        if (intent != null) payment.setStripePaymentIntentId(intent);
        payment.setGatewayTxnRef(sessionId);
        if ("PAID".equals(state)) payment.setStatus(PaymentStatus.SUCCEEDED);
        else if (payment.getStatus() != PaymentStatus.SUCCEEDED && !payment.isVerifiedRelease()) payment.setStatus(PaymentStatus.PENDING);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StripeHostedGateway.Snapshot create(HostedCheckout reserved) throws StripeException {
        guard.requirePayable(reserved.getOrderId());
        var payment = payments.findById(reserved.getPaymentId()).orElseThrow();
        var checkout = checkouts.lockByOrderId(reserved.getOrderId()).orElseThrow();
        if (!payment.getId().equals(checkout.getPaymentId()) || checkout.getGeneration() != reserved.getGeneration()
                || checkout.getSessionId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout session changed; read again");
        }
        var response = orders.read(checkout.getOrderId(), checkout.getUserId(), "hosted-checkout");
        var order = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || order == null || !checkout.getOrderId().equals(order.getId())
                || !checkout.getUserId().equals(order.getUserId()) || order.getTotalAmount() == null
                || !"USD".equals(order.getTotalAmount().getCurrency()) || order.getTotalAmount().getAmount() == null
                || checkout.getAmount().compareTo(order.getTotalAmount().getAmount()) != 0
                || !java.util.Set.of("PENDING", "CONFIRMED").contains(order.getStatus())
                || Boolean.FALSE.equals(order.getInventoryReady())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is not payable");
        }
        return stripe.create(checkout);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPaid(Long orderId) {
        if (guard.lock(orderId)) return;
        var existing = checkouts.findById(orderId).orElseThrow();
        var payment = payments.findById(existing.getPaymentId()).orElseThrow();
        var checkout = checkouts.lockByOrderId(orderId).orElseThrow();
        if (!"PAID".equals(checkout.getSessionState()) || payment.getStatus() != PaymentStatus.SUCCEEDED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout is not paid");
        var marked = orders.markPaid(orderId, checkout.getPaymentId(), "HOSTED-" + orderId, checkout.getUserId(), "hosted-checkout");
        if (!marked.getStatusCode().is2xxSuccessful())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to verify checkout; read status before trying again");
    }
}
