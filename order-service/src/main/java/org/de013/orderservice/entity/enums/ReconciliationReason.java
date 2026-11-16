package org.de013.orderservice.entity.enums;

/** Canonical reason codes for reconciliation classification and persisted evidence. */
public enum ReconciliationReason {
    SUCCESS_WITH_CLOSED_OR_RELEASED,
    HOLD_WITH_INVENTORY,
    PAYMENT_ORDER_MISMATCH,
    INVENTORY_MISMATCH,
    UNKNOWN_OR_STALE
}
