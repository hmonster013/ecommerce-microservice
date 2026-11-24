package org.de013.orderservice.service.impl;

import java.util.Set;
import java.util.UUID;

/** A positive observation of prior payment or fulfillment always blocks compensation. */
final class CompensationOrder {
    record Facts(String status, String phase, UUID closeOperation, UUID paymentCloseOperation,
                 Long acceptedPaymentId, String acceptedPaymentNumber, boolean paidEvent,
                 boolean fulfillmentOrDelivery, boolean supportedCheckout) { }

    private static final Set<String> ALLOWED = Set.of("CANCELLED/CLOSED", "CANCELLED/CLOSURE_HOLD",
            "PENDING/CLOSURE_HOLD", "ON_HOLD/CLOSURE_HOLD", "PENDING/RECONCILIATION", "ON_HOLD/RECONCILIATION");

    private CompensationOrder() { }

    static String holdReason(Facts facts) {
        if (facts == null || !facts.supportedCheckout()) return "LEGACY_HISTORY_UNPROVEN";
        if (facts.acceptedPaymentId() != null || facts.acceptedPaymentNumber() != null || facts.paidEvent()
                || facts.fulfillmentOrDelivery()) return "PRIOR_PAID_OR_FULFILLED";
        if (!ALLOWED.contains(facts.status() + "/" + facts.phase())) return "ORDER_STATE_NOT_ALLOWED";
        if (facts.closeOperation() == null || !facts.closeOperation().equals(facts.paymentCloseOperation()))
            return "CLOSE_OPERATION_MISMATCH";
        return null;
    }
}
