package org.de013.paymentservice.service;

import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.client.HostedOrderClient;
import org.de013.paymentservice.config.PaymentGatewayConfig;
import org.de013.paymentservice.dto.external.OrderDto;
import org.de013.paymentservice.dto.payment.HostedCheckoutResponse;
import org.de013.paymentservice.entity.HostedCheckout;
import org.de013.paymentservice.gateway.stripe.StripeHostedGateway;
import org.de013.paymentservice.repository.HostedCheckoutRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class HostedCheckoutService {
    private static final java.util.Set<String> PAID_ORDER_STATES = java.util.Set.of("PAID", "PROCESSING", "PREPARING",
            "SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED", "COMPLETED", "PARTIALLY_SHIPPED", "PARTIALLY_DELIVERED");
    private final HostedOrderClient orders;
    private final HostedCheckoutRepository checkouts;
    private final HostedCheckoutStore store;
    private final StripeHostedGateway stripe;
    private final PaymentGatewayConfig config;
    private final org.de013.paymentservice.service.validation.PaymentRequestValidator eligibility;

    public HostedCheckoutResponse start(Long orderId, String userId) {
        var order = ownedOrder(orderId, userId);
        eligibility.validateUserCanMakePayment(userId);
        if (!java.util.Set.of("PENDING", "CONFIRMED").contains(order.getStatus()) && !PAID_ORDER_STATES.contains(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is not payable");
        }
        var checkout = checkouts.findById(orderId).orElse(null);
        if (checkout == null) {
            if (!"PENDING".equals(order.getStatus()) && !"CONFIRMED".equals(order.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is not payable");
            }
            try {
                checkout = store.reserve(orderId, userId, order.getTotalAmount().getAmount(), returnUrl());
            } catch (DataIntegrityViolationException concurrent) {
                if (!PaymentReservation.isOrderConflict(concurrent) && !PaymentReservation.isHostedNumberConflict(concurrent)) throw concurrent;
                checkout = checkouts.findById(orderId).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.CONFLICT, "Order already has a blocking payment"));
            }
        }
        checkOwnerAndAmount(checkout, order, userId);
        try {
            if (checkout.getSessionId() != null) {
                var result = refresh(checkout, order);
                if (!"EXPIRED".equals(result.state())) return result;
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Expired session requires reconciliation");
            }
            // Stripe only guarantees idempotency retention for at least 24 hours.
            if (checkout.getReservedAt().isBefore(Instant.now().minus(Duration.ofHours(23)))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Unknown session requires reconciliation; do not create another payment");
            }
            var created = store.create(checkout);
            return apply(checkout, order, created);
        } catch (StripeException exception) {
            log.warn("Stripe hosted session outcome unknown for order {}", orderId);
            throw unavailable();
        }
    }

    public HostedCheckoutResponse read(Long orderId, String userId) {
        var order = ownedOrder(orderId, userId);
        var checkout = checkouts.findById(orderId).orElse(null);
        if (checkout == null) return response(order, "NOT_STARTED", null, null);
        checkOwnerAndAmount(checkout, order, userId);
        if (checkout.getSessionId() == null) return response(order, "UNKNOWN", null, "HOSTED-" + orderId);
        try {
            return refresh(checkout, order);
        } catch (StripeException exception) {
            log.warn("Stripe hosted status unavailable for order {}", orderId);
            throw unavailable();
        }
    }

    public void refreshWebhook(Long orderId, String sessionId) {
        var checkout = checkouts.findById(orderId).orElseThrow(this::unavailable);
        // A return/create timeout can race the webhook; let Stripe retry rather than acknowledge a lost event.
        if (!sessionId.equals(checkout.getSessionId())) throw unavailable();
        var response = orders.read(orderId, checkout.getUserId(), "hosted-checkout");
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) throw unavailable();
        var order = response.getBody();
        if (!checkout.getUserId().equals(order.getUserId()) || !orderId.equals(order.getId())
                || order.getTotalAmount() == null || !"USD".equals(order.getTotalAmount().getCurrency())) throw unavailable();
        checkOwnerAndAmount(checkout, order, checkout.getUserId());
        try { refresh(checkout, order); }
        catch (StripeException exception) { throw unavailable(); }
    }

    private HostedCheckoutResponse refresh(HostedCheckout checkout, OrderDto order) throws StripeException {
        return apply(checkout, order, stripe.retrieve(checkout.getSessionId()));
    }

    private HostedCheckoutResponse apply(HostedCheckout checkout, OrderDto order, StripeHostedGateway.Snapshot snapshot) {
        long cents = checkout.getAmount().movePointRight(2).longValueExact();
        if (snapshot.id() == null || (checkout.getSessionId() != null && !checkout.getSessionId().equals(snapshot.id()))
                || !Long.valueOf(cents).equals(snapshot.amount())
                || !"usd".equals(snapshot.currency()) || !order.getId().toString().equals(snapshot.reference())) {
            throw unavailable();
        }
        String state = "paid".equals(snapshot.paymentStatus()) ? "PAID"
                : "expired".equals(snapshot.status()) ? "EXPIRED"
                : "open".equals(snapshot.status()) ? "OPEN" : "PENDING";
        if ("PAID".equals(state) && (!"complete".equals(snapshot.status()) || snapshot.paymentIntent() == null)) throw unavailable();
        if ("PAID".equals(state) && !java.util.Set.of("PENDING", "CONFIRMED").contains(order.getStatus()) && !PAID_ORDER_STATES.contains(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Paid session requires order reconciliation");
        }
        store.record(order.getId(), checkout.getGeneration(), snapshot.id(), state, snapshot.paymentIntent());
        if ("PAID".equals(state)) {
            store.markPaid(order.getId());
        }
        String url = "OPEN".equals(state) ? trustedStripeUrl(snapshot.url()) : null;
        return response(order, state, url, "HOSTED-" + order.getId());
    }

    private OrderDto ownedOrder(Long orderId, String userId) {
        var result = orders.read(orderId, userId, "hosted-checkout");
        if (!result.getStatusCode().is2xxSuccessful() || result.getBody() == null) throw unavailable();
        var order = result.getBody();
        if (order.getUserId() == null) throw unavailable();
        if (userId == null || !userId.equals(order.getUserId())) throw new AccessDeniedException("Checkout owner mismatch");
        if (!orderId.equals(order.getId()) || order.getTotalAmount() == null
                || !"USD".equals(order.getTotalAmount().getCurrency()) || order.getTotalAmount().getAmount() == null
                || order.getTotalAmount().getAmount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hosted checkout requires canonical USD order money");
        }
        try { order.getTotalAmount().getAmount().movePointRight(2).longValueExact(); }
        catch (ArithmeticException invalid) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid USD amount precision"); }
        return order;
    }

    private void checkOwnerAndAmount(HostedCheckout checkout, OrderDto order, String userId) {
        if (!userId.equals(checkout.getUserId())) throw new AccessDeniedException("Checkout owner mismatch");
        if (order.getTotalAmount() == null || order.getTotalAmount().getAmount() == null
                || checkout.getAmount().compareTo(order.getTotalAmount().getAmount()) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order amount changed; reconcile payment first");
        }
    }

    private String returnUrl() {
        String value = config.getGateways().getStripe().getCheckoutReturnUrl();
        try {
            var uri = URI.create(value);
            if ((!"https".equals(uri.getScheme()) && !("http".equals(uri.getScheme()) && "localhost".equals(uri.getHost())))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || !"/checkout/result".equals(uri.getPath())) throw unavailable();
            return value;
        } catch (IllegalArgumentException | NullPointerException invalid) { throw unavailable(); }
    }

    private String trustedStripeUrl(String value) {
        try {
            var uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || !"checkout.stripe.com".equals(uri.getHost()) || uri.getUserInfo() != null) throw unavailable();
            return value;
        } catch (IllegalArgumentException | NullPointerException invalid) { throw unavailable(); }
    }

    private HostedCheckoutResponse response(OrderDto order, String state, String url, String paymentNumber) {
        return new HostedCheckoutResponse(order.getId(), order.getOrderNumber(), state,
                order.getTotalAmount().getAmount(), "USD", url, paymentNumber);
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to verify checkout; read status before trying again");
    }

}
