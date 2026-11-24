package org.de013.orderservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.de013.orderservice.client.InventoryReservationClient;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.entity.enums.ReconciliationReason;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "order.reconciliation.enabled", havingValue = "true")
@Slf4j
class ReconciliationJob {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ReconciliationStore store;
    private final PaymentReconciliationClient payment;
    private final InventoryReservationClient catalog;
    private final ObjectMapper mapper;

    ReconciliationJob(JdbcTemplate jdbc, TransactionTemplate tx, ReconciliationStore store,
                      PaymentReconciliationClient payment, InventoryReservationClient catalog, ObjectMapper mapper) {
        this.jdbc = jdbc; this.tx = tx; this.store = store; this.payment = payment; this.catalog = catalog; this.mapper = mapper;
    }

    record Local(ReconciliationClassifier.OrderFact order, List<ReconciliationClassifier.Intent> intents) { }
    record Observation(Instant observedAt, String status, InventoryReservationClient.Reservation reservation) { }
    record PaymentObservation(Instant observedAt, String status, Integer httpStatus, String errorCategory) { }
    record Evidence(Local initial, Local current, PaymentObservation paymentObservation, ReconciliationClassifier.PaymentSnapshot payment,
                    List<Observation> catalog) { }

    @Scheduled(fixedDelayString = "${order.reconciliation.delay-ms:60000}")
    void run() {
        seed();
        for (var claim : store.claim(50, Duration.ofMinutes(15))) {
            try { process(claim); }
            catch (Exception e) { log.warn("Reconciliation deferred for order {}: {}", claim.orderId(), e.toString()); }
        }
    }

    private void seed() {
        tx.executeWithoutResult(s -> {
            long cursor = jdbc.queryForObject("SELECT last_order_id FROM reconciliation_cursor WHERE id=1 FOR UPDATE", Long.class);
            var ids = jdbc.queryForList("SELECT id FROM orders WHERE id>? ORDER BY id LIMIT 50", Long.class, cursor);
            if (ids.isEmpty()) { jdbc.update("UPDATE reconciliation_cursor SET last_order_id=0 WHERE id=1"); return; }
            for (long id : ids) jdbc.update("INSERT INTO reconciliation_finding(order_id) VALUES (?) ON CONFLICT DO NOTHING", id);
            jdbc.update("UPDATE reconciliation_cursor SET last_order_id=? WHERE id=1", ids.get(ids.size() - 1));
        });
    }

    private Local local(long id, boolean lock) {
        var orders = jdbc.query("SELECT id,status,allocation_phase,accepted_payment_id,accepted_payment_number,total_amount,currency FROM orders WHERE id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> new ReconciliationClassifier.OrderFact(rs.getLong(1), OrderStatus.valueOf(rs.getString(2)), rs.getString(3), (Long) rs.getObject(4), rs.getString(5), rs.getBigDecimal(6), rs.getString(7)), id);
        if (orders.isEmpty()) return null;
        var intents = jdbc.query("SELECT operation_id,target_kind,target_id,quantity,owner_id,state FROM inventory_allocation_intents WHERE order_id=? ORDER BY operation_id" + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> new ReconciliationClassifier.Intent(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3), rs.getInt(4), rs.getString(5), rs.getString(6)), id);
        return new Local(orders.get(0), intents);
    }

    private void process(ReconciliationStore.Claim claim) {
        Local initial = tx.execute(s -> local(claim.orderId(), true));
        if (initial == null) return;
        ReconciliationClassifier.PaymentSnapshot paymentFact;
        PaymentObservation paymentObservation;
        try {
            paymentFact = payment.read(claim.orderId());
            paymentObservation = new PaymentObservation(Instant.now(), "OBSERVED", null, null);
        } catch (FeignException e) {
            paymentFact = null;
            paymentObservation = new PaymentObservation(Instant.now(), e.status() == 404 ? "MISSING" : "FAILED", e.status() > 0 ? e.status() : null, "HTTP_OR_NETWORK");
        } catch (RuntimeException e) {
            paymentFact = null;
            paymentObservation = new PaymentObservation(Instant.now(), "FAILED", null, "CLIENT_ERROR");
        }
        var observations = new ArrayList<Observation>();
        var inventory = new ArrayList<ReconciliationClassifier.InventoryFact>();
        for (var intent : initial.intents()) {
            InventoryReservationClient.Reservation remote = null;
            String status = "OBSERVED";
            try { remote = catalog.lookup(intent.operationId()); }
            catch (FeignException.NotFound e) { status = "MISSING"; }
            catch (RuntimeException e) { status = "FAILED"; }
            observations.add(new Observation(Instant.now(), status, remote));
            inventory.add(new ReconciliationClassifier.InventoryFact(intent, remote));
        }
        var reasons = EnumSet.noneOf(ReconciliationReason.class);
        reasons.addAll(ReconciliationClassifier.classify(initial.order(), paymentFact, inventory));
        var observedPayment = paymentFact;
        var observedPaymentResult = paymentObservation;
        tx.executeWithoutResult(s -> {
            Local current = local(claim.orderId(), true);
            if (!initial.equals(current)) reasons.add(ReconciliationReason.UNKNOWN_OR_STALE);
            try {
                store.complete(claim, mapper.writeValueAsString(new Evidence(initial, current, observedPaymentResult, observedPayment, observations)),
                        mapper.writeValueAsString(reasons), Duration.ofMinutes(5));
            } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
        });
    }
}
