package org.de013.orderservice.service.impl;

import feign.FeignException;
import java.sql.Connection;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.orderservice.client.InventoryReservationClient;
import org.de013.orderservice.client.InventoryReservationClient.Request;
import org.de013.orderservice.client.InventoryReservationClient.Reservation;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.repository.OrderRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class AllocationRecovery {
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final OrderRepository orders;
    private final InventoryReservationClient catalog;
    private long recoveryCursor;

    @Scheduled(fixedDelayString = "${order.allocation.recovery-delay-ms:5000}")
    public synchronized void recover() {
        var ids = jdbc.queryForList("SELECT id FROM orders WHERE allocation_phase IN ('ALLOCATING','COMPENSATING','FULFILL_PENDING') AND id > ? ORDER BY id LIMIT 50", Long.class, recoveryCursor);
        if (ids.isEmpty()) {
            recoveryCursor = 0;
            return;
        }
        for (Long id : ids) {
            recoveryCursor = id;
            process(id);
        }
    }

    public void process(long id) {
        // PostgreSQL session advisory lock is per-order and survives transaction commits, not connection loss.
        try (Connection connection = dataSource.getConnection(); var lock = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            lock.setLong(1, id);
            try (var rs = lock.executeQuery()) { rs.next(); if (!rs.getBoolean(1)) return; }
            try { processLocked(id); }
            finally { try (var unlock = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) { unlock.setLong(1, id); unlock.execute(); } }
        } catch (Exception e) { log.warn("Allocation recovery deferred for order {}: {}", id, e.toString()); }
    }

    private void processLocked(long id) {
        String phase = jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?", String.class, id);
        if (!java.util.Set.of("ALLOCATING", "COMPENSATING", "FULFILL_PENDING").contains(phase)) return;
        var lines = jdbc.query("SELECT operation_id,target_kind,target_id,quantity,owner_id,state FROM inventory_allocation_intents WHERE order_id=? ORDER BY operation_id", (rs, n) -> new Line(new Request(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3), rs.getInt(4), rs.getString(5)), rs.getString(6)), id);
        for (var line : lines) {
            if (phase.equals("ALLOCATING") && line.state().equals("RESERVED")) continue;
            if (phase.equals("COMPENSATING") && (line.state().equals("REJECTED") || line.state().equals("RELEASED"))) continue;
            if (phase.equals("FULFILL_PENDING") && line.state().equals("FULFILLED")) continue;
            if (!currentPhase(id, phase)) return;
            Reservation remote = lookup(line.request());
            if (remote == null && !phase.equals("FULFILL_PENDING")) {
                if (!currentPhase(id, phase)) return;
                remote = reserve(line.request());
            }
            if (remote == null) return;
            if (!matches(line.request(), remote)) { reconcile(id, phase); return; }
            String state = remote.state();
            if (phase.equals("ALLOCATING") && state.equals("REJECTED")) {
                apply(id, phase, line.request().operationId(), state, "COMPENSATING");
                processLocked(id);
                return;
            }
            if (!currentPhase(id, phase)) return;
            if (phase.equals("COMPENSATING") && state.equals("RESERVED")) remote = terminal(line.request(), false);
            else if (phase.equals("FULFILL_PENDING") && state.equals("RESERVED")) remote = terminal(line.request(), true);
            if (remote == null) return;
            if (!matches(line.request(), remote)) { reconcile(id, phase); return; }
            state = remote.state();
            if (!(phase.equals("ALLOCATING") && state.equals("RESERVED")) && !(phase.equals("COMPENSATING") && (state.equals("RELEASED") || state.equals("REJECTED"))) && !(phase.equals("FULFILL_PENDING") && state.equals("FULFILLED"))) { reconcile(id, phase); return; }
            apply(id, phase, line.request().operationId(), state, null);
        }
        tx.executeWithoutResult(status -> orders.lockForPayment(id).ifPresent(order -> {
            if (!phase.equals(order.getAllocationPhase())) return;
            String allowed = phase.equals("ALLOCATING") ? "RESERVED" : phase.equals("COMPENSATING") ? "RELEASED,REJECTED" : "FULFILLED";
            Integer total = jdbc.queryForObject("SELECT count(*) FROM inventory_allocation_intents WHERE order_id=?", Integer.class, id);
            Integer incomplete = jdbc.queryForObject("SELECT count(*) FROM inventory_allocation_intents WHERE order_id=? AND NOT (state = ANY(string_to_array(?, ',')))", Integer.class, id, allowed);
            if (total == null || total == 0 || incomplete == null || incomplete != 0) return;
            if (phase.equals("ALLOCATING")) { order.setAllocationPhase("READY"); order.setStatus(OrderStatus.PENDING); }
            else if (phase.equals("COMPENSATING")) { order.setAllocationPhase("FAILED"); order.setStatus(OrderStatus.FAILED); }
            else order.setAllocationPhase("FULFILLED");
        }));
    }

    private record Line(Request request, String state) { }
    private boolean matches(Request r, Reservation s) { return r.operationId().equals(s.operationId()) && r.targetKind().equals(s.targetKind()) && r.targetId().equals(s.targetId()) && r.quantity().equals(s.quantity()) && r.owner().equals(s.owner()); }
    private Reservation lookup(Request r) { try { return catalog.lookup(r.operationId()); } catch (FeignException.NotFound e) { return null; } }
    private Reservation reserve(Request r) { try { return catalog.reserve(r); } catch (FeignException.Conflict e) { return lookup(r); } }
    private Reservation terminal(Request r, boolean fulfill) { try { return fulfill ? catalog.fulfill(r.operationId()) : catalog.release(r.operationId()); } catch (FeignException.Conflict | FeignException.NotFound e) { return lookup(r); } }
    private boolean currentPhase(long id, String expected) {
        return Boolean.TRUE.equals(tx.execute(s -> orders.lockForPayment(id).map(o -> expected.equals(o.getAllocationPhase())).orElse(false)));
    }
    private void reconcile(long id, String expected) {
        tx.executeWithoutResult(s -> orders.lockForPayment(id).ifPresent(o -> {
            if (expected.equals(o.getAllocationPhase())) o.setAllocationPhase("RECONCILIATION");
        }));
    }
    private void apply(long id, String expected, UUID operation, String state, String nextPhase) {
        tx.executeWithoutResult(s -> orders.lockForPayment(id).ifPresent(o -> {
            if (!expected.equals(o.getAllocationPhase())) return;
            int changed = jdbc.update("UPDATE inventory_allocation_intents SET state=? WHERE order_id=? AND operation_id=? AND (state='PENDING' OR state='RESERVED' OR state=?)", state, id, operation, state);
            if (changed == 1 && nextPhase != null) o.setAllocationPhase(nextPhase);
        }));
    }
}
