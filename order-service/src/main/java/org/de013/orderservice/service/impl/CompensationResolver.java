package org.de013.orderservice.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import org.de013.orderservice.client.InventoryReservationClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
class CompensationResolver {
    private final CompensationStore store;
    private final JdbcTemplate jdbc;
    private final PaymentReconciliationClient payment;
    private final InventoryReservationClient catalog;
    private final ObjectMapper mapper;
    @Value("${order.compensation.enabled:false}") private boolean enabled;

    CompensationResolver(CompensationStore store, JdbcTemplate jdbc, PaymentReconciliationClient payment,
                         InventoryReservationClient catalog, ObjectMapper mapper) {
        this.store=store; this.jdbc=jdbc; this.payment=payment; this.catalog=catalog; this.mapper=mapper;
    }

    @Scheduled(fixedDelayString="${order.compensation.delay-ms:60000}")
    void scheduled() { if (enabled) processOne(); }

    void processOne() {
        var claim = store.claim(Duration.ofMinutes(5));
        if (claim == null) return;
        try { if(claim.recurrence()) recur(claim); else resolve(claim); }
        catch (RuntimeException e) { if(claim.recurrence()) store.recurrence(claim,"OBSERVATION_FAILED"); else store.retry(claim, Duration.ofMinutes(1)); }
    }

    private void recur(CompensationStore.Claim claim) {
        try {
            var expected=mapper.readValue(claim.proofSnapshot(),PaymentReconciliationClient.ProofResponse.class);
            var current=payment.proof(claim.orderId(),claim.requestId());
            if(current==null || !"COMPENSATED".equals(current.status()) || current.orderId()!=claim.orderId()
                    || !claim.requestId().equals(current.operationId()) || current.findingRevision()!=claim.revision()
                    || !Objects.equals(expected.proofId(),current.proofId()) || !Objects.equals(expected.proofVersion(),current.proofVersion())
                    || current.proofVersion()==null || current.financialVersion()!=current.proofVersion()
                    || !Objects.equals(expected.paymentId(),current.paymentId()) || !Objects.equals(expected.paymentNumber(),current.paymentNumber())
                    || current.amount()==null || expected.amount()==null || current.amount().compareTo(expected.amount())!=0
                    || !Objects.equals(expected.currency(),current.currency()) || !Objects.equals(expected.closeOperationId(),current.closeOperationId())
                    || current.observedAt()==null || current.observedAt().isAfter(Instant.now())) { store.recurrence(claim,"PAYMENT_CHANGED"); return; }
            JsonNode snapshot=mapper.readTree(claim.localSnapshot());
            for(JsonNode intent:snapshot.path("intents")) {
                UUID op=uuid(intent.path("operationId"));
                if(op==null) { store.recurrence(claim,"INVENTORY_CHANGED"); return; }
                var remote=catalog.lookup(op);
                if(remote==null || !op.equals(remote.operationId()) || !Objects.equals(intent.path("targetKind").asText(),remote.targetKind())
                        || !Objects.equals(intent.path("targetId").asLong(),remote.targetId()) || !Objects.equals(intent.path("quantity").asInt(),remote.quantity())
                        || !Objects.equals(intent.path("owner").asText(),remote.owner()) || !Objects.equals(intent.path("state").asText(),remote.state())
                        || !("RELEASED".equals(remote.state()) || "REJECTED".equals(remote.state()))) { store.recurrence(claim,"INVENTORY_CHANGED"); return; }
            }
            store.recurrence(claim,null);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) { store.recurrence(claim,"INVALID_EVIDENCE"); }
    }

    private void resolve(CompensationStore.Claim claim) {
        try {
            JsonNode snapshot=mapper.readTree(claim.localSnapshot()), order=snapshot.path("order"), checkout=snapshot.path("checkout");
            UUID close=uuid(order.path("closeOperation"));
            boolean supported=order.path("checkoutKey").isTextual() && checkout.path("requestSnapshotHash").isTextual()
                    && order.path("userId").asText().equals(checkout.path("userId").asText())
                    && order.path("userId").asText().equals(checkout.path("requestUserId").asText())
                    && "USD".equals(checkout.path("requestCurrency").asText()) && "USD".equals(order.path("currency").asText())
                    && "CARD".equals(checkout.path("requestPaymentMethod").asText()) && close != null;
            boolean delivered=!order.path("actualDeliveryDate").isNull() || !order.path("deletedAt").isNull()
                    || snapshot.path("items").findValuesAsText("status").stream().anyMatch(s -> !s.equals("PENDING"));
            var facts = new CompensationOrder.Facts(order.path("status").asText(),order.path("allocationPhase").asText(),close,close,
                    order.path("acceptedPaymentId").isNumber() ? order.path("acceptedPaymentId").asLong() : null,
                    order.path("acceptedPaymentNumber").isTextual() ? order.path("acceptedPaymentNumber").asText() : null,false,delivered,supported);
            String reason=CompensationOrder.holdReason(facts);
            if (reason != null) { store.hold(claim,reason); return; }
            String evidence=jdbc.queryForObject("SELECT evidence::text FROM reconciliation_revision WHERE order_id=? AND revision=?",String.class,claim.orderId(),claim.revision());
            var initial=mapper.treeToValue(mapper.readTree(evidence).path("payment"),ReconciliationClassifier.PaymentSnapshot.class);
            var current=payment.read(claim.orderId());
            var candidate=unique(initial,claim.orderId(),close);
            var now=unique(current,claim.orderId(),close);
            if (candidate == null || now == null || !candidate.paymentId().equals(now.paymentId())
                    || !candidate.paymentNumber().equals(now.paymentNumber()) || candidate.amount().compareTo(now.amount()) != 0
                    || !candidate.currency().equals(now.currency()) || !candidate.gateway().equals(now.gateway())
                    || candidate.amount().compareTo(order.path("amount").decimalValue()) != 0
                    || !candidate.currency().equals(order.path("currency").asText())) { store.hold(claim,"PAYMENT_FACTS_MISMATCH"); return; }
            var lines=new ArrayList<CompensationInventory.Line>();
            for (JsonNode item:snapshot.path("items")) {
                if (!item.path("actualDeliveryDate").isNull() || !item.path("deletedAt").isNull()) { store.hold(claim,"PRIOR_DELIVERY"); return; }
                lines.add(new CompensationInventory.Line(item.path("productId").asText(),item.path("quantity").asInt()));
            }
            var observed=new ArrayList<CompensationInventory.Observation>();
            for (JsonNode item:snapshot.path("intents")) {
                UUID op=uuid(item.path("operationId"));
                if(op==null) { store.hold(claim,"INCOMPLETE_INVENTORY"); return; }
                var intent=new CompensationInventory.Intent(op,item.path("targetKind").asText(),item.path("targetId").asLong(),item.path("quantity").asInt(),item.path("owner").asText(),item.path("state").asText());
                observed.add(new CompensationInventory.Observation(intent,catalog.lookup(op)));
            }
            var inventory=CompensationInventory.validate(order.path("userId").asText(),lines,observed);
            if(inventory.reason()!=null) { store.hold(claim,inventory.reason()); return; }
            var request=new PaymentReconciliationClient.ProofRequest(claim.requestId(),claim.revision(),candidate.paymentId(),candidate.paymentNumber(),candidate.amount(),candidate.currency(),close);
            PaymentReconciliationClient.ProofResponse proof;
            try { proof=payment.verify(claim.orderId(),request); }
            catch (feign.FeignException.Conflict e) { store.hold(claim,"PAYMENT_OPERATION_CONFLICT"); return; }
            if (proof != null && "VERIFYING".equals(proof.status())) { store.retry(claim,Duration.ofMinutes(1)); return; }
            if (!valid(proof,claim,request)) { store.hold(claim,"PROOF_MISMATCH_OR_HOLD"); return; }
            if (!store.finish(claim,mapper.writeValueAsString(proof),mapper.writeValueAsString(inventory.updates())))
                org.slf4j.LoggerFactory.getLogger(CompensationResolver.class).info("Compensation finalization rejected for request {}",claim.requestId());
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) { store.hold(claim,"INVALID_EVIDENCE"); }
    }

    private static UUID uuid(JsonNode node) { return node.isTextual() ? UUID.fromString(node.asText()) : null; }

    private static ReconciliationClassifier.Attempt unique(ReconciliationClassifier.PaymentSnapshot snapshot,long id,UUID close) {
        if(snapshot==null || !Objects.equals(snapshot.orderId(),id) || !Boolean.TRUE.equals(snapshot.guardPresent())
                || snapshot.observedAt()==null || !Objects.equals(close,snapshot.closeOperationId()) || snapshot.attempts()==null || snapshot.attempts().isEmpty()) return null;
        ReconciliationClassifier.Attempt success=null;
        for(var a:snapshot.attempts()) {
            if(a==null || a.paymentId()==null || a.paymentId()<=0 || a.paymentNumber()==null || a.paymentNumber().isBlank() || a.status()==null
                    || a.successfulChargeOrCapture()==null || a.amount()==null || a.currency()==null || a.gateway()==null) return null;
            if("SUCCEEDED".equals(a.status()) || a.successfulChargeOrCapture()) { if(success!=null || !"STRIPE".equals(a.gateway())) return null; success=a; }
        }
        return success;
    }

    private static boolean valid(PaymentReconciliationClient.ProofResponse p,CompensationStore.Claim c,PaymentReconciliationClient.ProofRequest r) {
        return p!=null && "COMPENSATED".equals(p.status()) && p.orderId()==c.orderId() && c.requestId().equals(p.operationId())
                && p.findingRevision()==c.revision() && p.proofId()!=null && p.proofVersion()!=null && p.financialVersion()==p.proofVersion()
                && p.observedAt()!=null && !p.observedAt().isAfter(Instant.now()) && Objects.equals(p.paymentId(),r.expectedPaymentId()) && Objects.equals(p.paymentNumber(),r.expectedPaymentNumber())
                && p.amount()!=null && p.amount().compareTo(r.expectedAmount())==0 && Objects.equals(p.currency(),r.expectedCurrency())
                && Objects.equals(p.closeOperationId(),r.expectedCloseOperationId());
    }
}
