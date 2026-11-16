package org.de013.orderservice.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.de013.orderservice.client.InventoryReservationClient.Reservation;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.entity.enums.ReconciliationReason;
import static org.de013.orderservice.entity.enums.ReconciliationReason.*;

/** Classifies sampled facts; an empty result is not a consistency or resolution claim. */
final class ReconciliationClassifier {
    record PaymentSnapshot(Long orderId, Instant observedAt, Boolean guardPresent, Boolean reconciliationHold,
                           UUID closeOperationId, String closeStatus, String closeReason, Instant closeSafeAt,
                           Boolean closeSafeProofPresent, List<Attempt> attempts) { }
    record Attempt(Long paymentId, String paymentNumber, String status, BigDecimal amount, String currency,
                   String gateway, Boolean successfulChargeOrCapture) { }
    record OrderFact(long id, OrderStatus status, String phase, Long acceptedPaymentId,
                     String acceptedPaymentNumber, BigDecimal amount, String currency) { }
    record Intent(UUID operationId, String targetKind, Long targetId, Integer quantity, String owner, String state) { }
    // A null reservation represents any missing or failed Catalog observation, including 404.
    record InventoryFact(Intent intent, Reservation reservation) { }

    static Set<ReconciliationReason> classify(OrderFact order, PaymentSnapshot payment, List<InventoryFact> inventory) {
        var reasons = EnumSet.noneOf(ReconciliationReason.class);
        boolean hold = order.status() == OrderStatus.ON_HOLD || "CLOSURE_HOLD".equals(order.phase())
                || "RECONCILIATION".equals(order.phase());
        boolean transitional = Set.of("ALLOCATING", "COMPENSATING", "FULFILL_PENDING", "CLOSING", "RELEASE_PENDING").contains(Objects.toString(order.phase(), ""));
        if (transitional || inventory == null || order.status() == null) reasons.add(UNKNOWN_OR_STALE);
        boolean released = false;
        if (inventory != null) for (var fact : inventory) {
            if (fact == null || fact.intent() == null || fact.reservation() == null) {
                reasons.add(UNKNOWN_OR_STALE);
                continue;
            }
            var i = fact.intent();
            var r = fact.reservation();
            if (i.operationId() == null || i.targetKind() == null || i.targetId() == null || i.quantity() == null || i.owner() == null
                    || r.operationId() == null || r.targetKind() == null || r.targetId() == null || r.quantity() == null || r.owner() == null) {
                reasons.add(UNKNOWN_OR_STALE);
            } else if (!i.operationId().equals(r.operationId()) || !i.targetKind().equals(r.targetKind())
                    || !i.targetId().equals(r.targetId()) || !i.quantity().equals(r.quantity()) || !i.owner().equals(r.owner())) {
                reasons.add(INVENTORY_MISMATCH);
            }
            if ("RELEASED".equals(r.state())) released = true;
            if (i.state() == null || r.state() == null) reasons.add(UNKNOWN_OR_STALE);
            else if (!transitional && Set.of("RESERVED", "RELEASED", "FULFILLED", "REJECTED").contains(i.state())) {
                if (!i.state().equals(r.state())) reasons.add(INVENTORY_MISMATCH);
            } else if (!Set.of("RESERVED", "RELEASED", "FULFILLED", "REJECTED").contains(i.state())) reasons.add(UNKNOWN_OR_STALE);
        }
        if (payment == null) {
            reasons.add(UNKNOWN_OR_STALE);
        } else {
            if (!Objects.equals(payment.orderId(), order.id()) || payment.observedAt() == null
                    || !Boolean.TRUE.equals(payment.guardPresent()) || payment.reconciliationHold() == null
                    || payment.closeSafeProofPresent() == null || payment.attempts() == null) reasons.add(UNKNOWN_OR_STALE);
            hold |= Boolean.TRUE.equals(payment.reconciliationHold()) || "HOLD".equals(payment.closeStatus());
            int successes = 0;
            boolean matchingSuccess = false;
            boolean completeAttempts = payment.attempts() != null;
            if (payment.attempts() != null) for (var attempt : payment.attempts()) {
                if (attempt == null) { reasons.add(UNKNOWN_OR_STALE); completeAttempts = false; continue; }
                if (attempt.status() == null || attempt.successfulChargeOrCapture() == null || attempt.paymentId() == null
                        || attempt.paymentNumber() == null || attempt.amount() == null || attempt.currency() == null || attempt.gateway() == null) reasons.add(UNKNOWN_OR_STALE);
                if (attempt.status() == null || attempt.successfulChargeOrCapture() == null || attempt.paymentId() == null || attempt.paymentNumber() == null) completeAttempts = false;
                boolean success = "SUCCEEDED".equals(attempt.status()) || Boolean.TRUE.equals(attempt.successfulChargeOrCapture());
                if (!success) continue;
                successes++;
                if (Objects.equals(order.acceptedPaymentId(), attempt.paymentId()) && Objects.equals(order.acceptedPaymentNumber(), attempt.paymentNumber()) && order.acceptedPaymentId() != null && order.acceptedPaymentNumber() != null) matchingSuccess = true;
                if (order.status() == OrderStatus.CANCELLED || "CLOSED".equals(order.phase()) || released)
                    reasons.add(SUCCESS_WITH_CLOSED_OR_RELEASED);
                if (order.status() != null && !order.status().isPaidState())
                    reasons.add(PAYMENT_ORDER_MISMATCH);
                if (order.acceptedPaymentId() != null && attempt.paymentId() != null && !order.acceptedPaymentId().equals(attempt.paymentId())
                        || order.acceptedPaymentNumber() != null && attempt.paymentNumber() != null && !order.acceptedPaymentNumber().equals(attempt.paymentNumber())
                        || order.amount() != null && attempt.amount() != null && order.amount().compareTo(attempt.amount()) != 0
                        || order.currency() != null && attempt.currency() != null && !order.currency().equals(attempt.currency())) reasons.add(PAYMENT_ORDER_MISMATCH);
                if (order.acceptedPaymentId() == null || order.acceptedPaymentNumber() == null || order.amount() == null || order.currency() == null)
                    reasons.add(UNKNOWN_OR_STALE);
            }
            if (successes > 1) reasons.add(PAYMENT_ORDER_MISMATCH);
            if (order.status() != null && (order.status().isPaidState() || order.acceptedPaymentId() != null || order.acceptedPaymentNumber() != null)) {
                if (order.acceptedPaymentId() == null || order.acceptedPaymentNumber() == null) reasons.add(UNKNOWN_OR_STALE);
                if (Boolean.TRUE.equals(payment.guardPresent()) && Objects.equals(payment.orderId(), order.id()) && payment.observedAt() != null && completeAttempts && !matchingSuccess)
                    reasons.add(PAYMENT_ORDER_MISMATCH);
            }
        }
        if (hold) reasons.add(HOLD_WITH_INVENTORY);
        return reasons;
    }
}
