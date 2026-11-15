package org.de013.orderservice.service.impl;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.orderservice.client.InventoryReservationClient;
import org.de013.orderservice.client.PaymentClosureClient;
import org.de013.orderservice.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutExpiryRecovery {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final OrderRepository orders;
    private final PaymentClosureClient payment;
    private final InventoryReservationClient inventory;
    @Value("${order.checkout-expiry.enabled:false}") private boolean enabled;
    private long cursor;

    @Scheduled(fixedDelayString = "${order.allocation.recovery-delay-ms:5000}")
    public synchronized void recover() {
        if (!enabled) return;
        var ids = jdbc.queryForList("SELECT id FROM orders WHERE id > ? AND (allocation_phase IN ('CLOSING','RELEASE_PENDING') OR (allocation_phase='READY' AND checkout_deadline <= CURRENT_TIMESTAMP)) ORDER BY id LIMIT 50", Long.class, cursor);
        if (ids.isEmpty()) { cursor = 0; return; }
        for (long id : ids) {
            cursor = id;
            try { process(id); } catch (Exception e) { log.warn("Checkout closure deferred for order {}: {}", id, e.toString()); }
        }
    }

    public void process(long id) {
        UUID claim = UUID.randomUUID();
        UUID operation = tx.execute(s -> orders.lockForPayment(id).map(order -> {
            if (!("CLOSING".equals(order.getAllocationPhase()) || "RELEASE_PENDING".equals(order.getAllocationPhase())
                    || ("READY".equals(order.getAllocationPhase()) && order.getCheckoutDeadline() != null
                    && !order.getCheckoutDeadline().isAfter(LocalDateTime.now())))) return null;
            int acquired = jdbc.update("UPDATE orders SET checkout_close_claim=?, checkout_close_claim_until=CURRENT_TIMESTAMP + interval '60 seconds' WHERE id=? AND (checkout_close_claim_until IS NULL OR checkout_close_claim_until <= CURRENT_TIMESTAMP)", claim, id);
            if (acquired != 1) return null;
            if ("READY".equals(order.getAllocationPhase()) && order.getCheckoutDeadline() != null
                    && !order.getCheckoutDeadline().isAfter(LocalDateTime.now())) {
                order.setCheckoutCloseOperation(UUID.randomUUID());
                order.setAllocationPhase("CLOSING");
            }
            return java.util.Set.of("CLOSING", "RELEASE_PENDING").contains(order.getAllocationPhase()) ? order.getCheckoutCloseOperation() : null;
        }).orElse(null));
        if (operation == null) return;
        try {
        if (!confirmSafe(id, operation, claim)) return;
        var lines = jdbc.query("SELECT operation_id,target_kind,target_id,quantity,owner_id,state FROM inventory_allocation_intents WHERE order_id=? ORDER BY operation_id",
                (rs, n) -> new Line(new InventoryReservationClient.Request(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3), rs.getInt(4), rs.getString(5)), rs.getString(6)), id);
        for (var line : lines) {
            if ("RELEASED".equals(line.state())) continue;
            if (!"RESERVED".equals(line.state()) || !confirmSafe(id, operation, claim)) return;
            boolean allowed = Boolean.TRUE.equals(tx.execute(s -> orders.lockForPayment(id).map(o -> operation.equals(o.getCheckoutCloseOperation()) && "RELEASE_PENDING".equals(o.getAllocationPhase()) && validClaim(id, claim)).orElse(false)));
            if (!allowed) return;
            var remote = inventory.release(line.request().operationId());
            if (remote == null || !line.request().operationId().equals(remote.operationId())
                    || !line.request().targetKind().equals(remote.targetKind()) || !line.request().targetId().equals(remote.targetId())
                    || !line.request().quantity().equals(remote.quantity()) || !line.request().owner().equals(remote.owner()) || !"RELEASED".equals(remote.state())) {
                hold(id, operation, claim, "INVENTORY_RELEASE_MISMATCH"); return;
            }
            tx.executeWithoutResult(s -> orders.lockForPayment(id).ifPresent(o -> {
                if (operation.equals(o.getCheckoutCloseOperation()) && "RELEASE_PENDING".equals(o.getAllocationPhase()) && validClaim(id, claim)) {
                    jdbc.update("UPDATE inventory_allocation_intents SET state='RELEASED' WHERE order_id=? AND operation_id=? AND state='RESERVED'", id, line.request().operationId());
                }
            }));
        }
        tx.executeWithoutResult(s -> orders.lockForPayment(id).ifPresent(o -> {
            if (operation.equals(o.getCheckoutCloseOperation()) && "RELEASE_PENDING".equals(o.getAllocationPhase()) && validClaim(id, claim)) {
                Integer remaining = jdbc.queryForObject("SELECT count(*) FROM inventory_allocation_intents WHERE order_id=? AND state <> 'RELEASED'", Integer.class, id);
                if (remaining != null && remaining == 0 && o.getStatus() == org.de013.orderservice.entity.enums.OrderStatus.PENDING) {
                    o.cancel("Checkout expired");
                    o.setAllocationPhase("CLOSED");
                }
            }
        }));
        } finally {
            jdbc.update("UPDATE orders SET checkout_close_claim=NULL, checkout_close_claim_until=NULL WHERE id=? AND checkout_close_claim=?", id, claim);
        }
    }

    private boolean confirmSafe(long id, UUID operation, UUID claim) {
        var result = payment.close(id, new PaymentClosureClient.Request(operation));
        if (result == null || !Long.valueOf(id).equals(result.orderId()) || !operation.equals(result.operationId())
                || !("SAFE".equals(result.status()) || "HOLD".equals(result.status()))) {
            hold(id, operation, claim, "PAYMENT_PROOF_MISMATCH"); return false;
        }
        return Boolean.TRUE.equals(tx.execute(s -> orders.lockForPayment(id).map(o -> {
            if (!operation.equals(o.getCheckoutCloseOperation()) || !("CLOSING".equals(o.getAllocationPhase()) || "RELEASE_PENDING".equals(o.getAllocationPhase())) || !validClaim(id, claim)) return false;
            if ("HOLD".equals(result.status())) {
                o.setAllocationPhase("CLOSURE_HOLD"); o.setCheckoutCloseHoldReason(result.reason() == null ? "PAYMENT_HOLD" : result.reason()); return false;
            }
            if (o.getCheckoutCloseSafeAt() == null) o.setCheckoutCloseSafeAt(LocalDateTime.now());
            o.setAllocationPhase("RELEASE_PENDING"); return true;
        }).orElse(false)));
    }

    private boolean validClaim(long id, UUID claim) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT checkout_close_claim=? AND checkout_close_claim_until > CURRENT_TIMESTAMP FROM orders WHERE id=?", Boolean.class, claim, id));
    }

    private void hold(long id, UUID operation, UUID claim, String reason) {
        tx.executeWithoutResult(s -> orders.lockForPayment(id).ifPresent(o -> {
            if (operation.equals(o.getCheckoutCloseOperation()) && ("CLOSING".equals(o.getAllocationPhase()) || "RELEASE_PENDING".equals(o.getAllocationPhase())) && validClaim(id, claim)) {
                o.setAllocationPhase("CLOSURE_HOLD"); o.setCheckoutCloseHoldReason(reason);
            }
        }));
    }

    private record Line(InventoryReservationClient.Request request, String state) { }
}
