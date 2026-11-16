package org.de013.orderservice.service.impl;

import static org.de013.orderservice.entity.enums.ReconciliationReason.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.de013.orderservice.client.InventoryReservationClient.Reservation;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.entity.enums.ReconciliationReason;
import org.de013.orderservice.service.impl.ReconciliationClassifier.*;
import org.junit.jupiter.api.Test;

class ReconciliationClassifierTest {
    private static final UUID OP = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final BigDecimal AMOUNT = new BigDecimal("12.00");
    private static final OrderFact PAID = new OrderFact(1, OrderStatus.PAID, "READY", 2L, "P2", AMOUNT, "USD");
    private static final Attempt SUCCESS = new Attempt(2L, "P2", "SUCCEEDED", AMOUNT, "USD", "gateway", false);
    private static final Intent INTENT = new Intent(OP, "PRODUCT", 3L, 1, "owner", "RESERVED");
    private static final Reservation RESERVED = new Reservation(OP, "PRODUCT", 3L, 1, "owner", "RESERVED", null);
    private static PaymentSnapshot payment(Attempt... attempts) {
        return new PaymentSnapshot(1L, Instant.EPOCH, true, false, null, null, null, null, false, List.of(attempts));
    }
    private static List<InventoryFact> inventory(Reservation r) { return List.of(new InventoryFact(INTENT, r)); }
    private record Case(OrderFact order, PaymentSnapshot payment, List<InventoryFact> inventory, Set<ReconciliationReason> expected) { }

    @Test void decisionTable() {
        var released = new Reservation(OP, "PRODUCT", 3L, 1, "owner", "RELEASED", null);
        var wrongOwner = new Reservation(OP, "PRODUCT", 3L, 1, "other", "RESERVED", null);
        var historical = new Attempt(2L, "P2", "REFUNDED", AMOUNT, "USD", "gateway", true);
        var mismatch = new Attempt(4L, "P4", "SUCCEEDED", new BigDecimal("13"), "EUR", "gateway", false);
        var missingFlag = new Attempt(2L, "P2", "PENDING", AMOUNT, "USD", "gateway", null);
        var cases = List.of(
                new Case(PAID, payment(SUCCESS), inventory(RESERVED), Set.of()),
                new Case(PAID, payment(historical), inventory(released), Set.of(SUCCESS_WITH_CLOSED_OR_RELEASED, INVENTORY_MISMATCH)),
                new Case(new OrderFact(1, OrderStatus.CANCELLED, "CLOSED", 2L, "P2", AMOUNT, "USD"), payment(SUCCESS), inventory(RESERVED), Set.of(SUCCESS_WITH_CLOSED_OR_RELEASED, PAYMENT_ORDER_MISMATCH)),
                new Case(new OrderFact(1, OrderStatus.PAID, "CLOSURE_HOLD", 2L, "P2", AMOUNT, "USD"), payment(SUCCESS), inventory(RESERVED), Set.of(HOLD_WITH_INVENTORY)),
                new Case(PAID, payment(SUCCESS, mismatch), inventory(wrongOwner), Set.of(PAYMENT_ORDER_MISMATCH, INVENTORY_MISMATCH)),
                new Case(new OrderFact(1, OrderStatus.PAID, "CLOSING", 2L, "P2", AMOUNT, "USD"), payment(SUCCESS), inventory(released), Set.of(UNKNOWN_OR_STALE, SUCCESS_WITH_CLOSED_OR_RELEASED)),
                new Case(PAID, null, inventory(null), Set.of(UNKNOWN_OR_STALE)),
                new Case(PAID, payment(missingFlag), inventory(RESERVED), Set.of(UNKNOWN_OR_STALE)),
                new Case(PAID, new PaymentSnapshot(1L, Instant.EPOCH, false, true, null, null, null, null, false, List.of()), inventory(RESERVED), Set.of(UNKNOWN_OR_STALE, HOLD_WITH_INVENTORY)),
                new Case(PAID, new PaymentSnapshot(1L, Instant.EPOCH, true, false, null, "HOLD", null, null, false, List.of()), inventory(RESERVED), Set.of(HOLD_WITH_INVENTORY, PAYMENT_ORDER_MISMATCH))
        );
        for (var c : cases) assertEquals(c.expected(), ReconciliationClassifier.classify(c.order(), c.payment(), c.inventory()), c.toString());
    }

    @Test void paidStateAndReverseMismatch() {
        for (var status : List.of(OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.PREPARING, OrderStatus.SHIPPED,
                OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, OrderStatus.COMPLETED,
                OrderStatus.PARTIALLY_SHIPPED, OrderStatus.PARTIALLY_DELIVERED)) {
            var order = new OrderFact(1, status, "READY", 2L, "P2", AMOUNT, "USD");
            assertEquals(Set.of(), ReconciliationClassifier.classify(order, payment(SUCCESS), inventory(RESERVED)));
            assertEquals(Set.of(PAYMENT_ORDER_MISMATCH), ReconciliationClassifier.classify(order, payment(), inventory(RESERVED)));
            assertEquals(Set.of(PAYMENT_ORDER_MISMATCH), ReconciliationClassifier.classify(order, payment(new Attempt(2L, "P2", "FAILED", AMOUNT, "USD", "gateway", false)), inventory(RESERVED)));
        }
        var missingId = new OrderFact(1, OrderStatus.PAID, "READY", null, "P2", AMOUNT, "USD");
        assertEquals(Set.of(PAYMENT_ORDER_MISMATCH, UNKNOWN_OR_STALE), ReconciliationClassifier.classify(missingId, payment(), inventory(RESERVED)));
    }
}
