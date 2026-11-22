package org.de013.orderservice.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Duration;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.HashMap;
import org.de013.orderservice.client.InventoryReservationClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties={"spring.cloud.config.enabled=false","spring.cloud.bus.enabled=false","eureka.client.enabled=false","spring.jpa.hibernate.ddl-auto=validate","spring.flyway.enabled=true","order.checkout-expiry.enabled=false","order.allocation.recovery-delay-ms=3600000"})
class CompensationResolverIntegrationTest {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",DB::getJdbcUrl); r.add("spring.datasource.username",DB::getUsername); r.add("spring.datasource.password",DB::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired CompensationResolver resolver;
    @Autowired CompensationStore store;
    @MockitoBean PaymentReconciliationClient payment;
    @MockitoBean InventoryReservationClient catalog;
    @BeforeEach void clearPriorDueWork() {
        jdbc.update("UPDATE compensation_request SET next_check_at=clock_timestamp()+interval '1 day' WHERE status IN ('REQUESTED','CLAIMED','COMPENSATED')");
    }
    record Fixture(long id, UUID request, UUID close, UUID intent) { }

    Fixture fixture() throws Exception {
        UUID close=UUID.randomUUID(), intent=UUID.randomUUID(), request=UUID.randomUUID(); String key=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO checkout_attempts(attempt_key,user_id,request_snapshot) VALUES (?,'owner','{\"userId\":\"owner\",\"currency\":\"USD\",\"paymentMethod\":\"CARD\"}')",key);
        long id=jdbc.queryForObject("INSERT INTO orders(order_number,user_id,status,order_type,allocation_phase,checkout_key,checkout_close_operation,total_amount,currency) VALUES (?,'owner','CANCELLED','STANDARD','CLOSED',?,?,12.50,'USD') RETURNING id",Long.class,UUID.randomUUID().toString(),key,close);
        jdbc.update("INSERT INTO order_items(order_id,product_id,sku,product_name,quantity,status) VALUES (?,'42','sku','product',1,'PENDING')",id);
        jdbc.update("INSERT INTO inventory_allocation_intents(operation_id,order_id,target_kind,target_id,quantity,owner_id,state) VALUES (?,?,'PRODUCT',42,1,'owner','RESERVED')",intent,id);
        var attempt=new ReconciliationClassifier.Attempt(9L,"pay","SUCCEEDED",new BigDecimal("12.50"),"USD","STRIPE",true);
        var snapshot=new ReconciliationClassifier.PaymentSnapshot(id,Instant.parse("2026-01-01T00:00:00Z"),true,true,close,"HOLD","LATE",null,false,List.of(attempt));
        jdbc.update("INSERT INTO reconciliation_finding(order_id,current_revision) VALUES (?,1)",id);
        jdbc.update("INSERT INTO reconciliation_revision(order_id,revision,evidence,reasons) VALUES (?,1,CAST(? AS jsonb),'[\"HOLD_WITH_INVENTORY\"]')",id,mapper.writeValueAsString(Map.of("payment",snapshot)));
        jdbc.queryForObject("SELECT compensation_submit(?,?,1,'review')",String.class,request,id);
        when(payment.read(id)).thenReturn(snapshot);
        when(catalog.lookup(intent)).thenReturn(new InventoryReservationClient.Reservation(intent,"PRODUCT",42L,1,"owner","RELEASED",null));
        return new Fixture(id,request,close,intent);
    }
    PaymentReconciliationClient.ProofResponse proof(Fixture f) {
        var evidence=mapper.valueToTree(Map.of("accountId","acct_fixture","livemode",false,"observedAt","2026-01-01T00:00:00Z",
                "charges",List.of(Map.of("id","ch_fixture","intentId","pi_fixture","amount",1250,"status","succeeded",
                        "refunds",List.of(Map.of("id","re_fixture","amount",1250,"status","succeeded"))))));
        return new PaymentReconciliationClient.ProofResponse(f.id,f.request,1,"COMPENSATED","REFUNDED",UUID.randomUUID(),3,3L,Instant.parse("2026-01-01T00:00:00Z"),9L,"pay",new BigDecimal("12.50"),"USD",f.close,evidence);
    }
    String status(Fixture f) { return jdbc.queryForObject("SELECT status FROM compensation_request WHERE request_id=?",String.class,f.request); }

    @Test void verifiedRefundFinalizesAtomicallyWithoutRemoteMutations() throws Exception {
        Fixture f=fixture(); when(payment.verify(eq(f.id),any())).thenReturn(proof(f));
        resolver.processOne();
        assertEquals("COMPENSATED",status(f));
        assertEquals("RELEASED",jdbc.queryForObject("SELECT state FROM inventory_allocation_intents WHERE operation_id=?",String.class,f.intent));
        assertEquals("CANCELLED/CLOSED",jdbc.queryForObject("SELECT status || '/' || allocation_phase FROM orders WHERE id=?",String.class,f.id));
        assertEquals("RESERVED/RELEASED",jdbc.queryForObject("SELECT (before_state->'intents'->0->>'state') || '/' || (after_state->'local'->'intents'->0->>'state') FROM compensation_event WHERE request_id=? AND outcome='COMPENSATED'",String.class,f.request));
        assertThrows(DataAccessException.class,()->jdbc.update("DELETE FROM compensation_event WHERE request_id=?",f.request));
        verify(catalog).lookup(f.intent); verifyNoMoreInteractions(catalog);
        verify(payment).read(f.id); verify(payment).verify(eq(f.id),argThat(p->p.operationId().equals(f.request))); verifyNoMoreInteractions(payment);
    }

    @Test void localCommitFailureAfterRemoteCommitRetriesSameOperation() throws Exception {
        Fixture f=fixture(); var calls=new AtomicInteger(); var proof=proof(f);
        when(payment.verify(eq(f.id),any())).thenAnswer(invocation->{ var p=invocation.getArgument(1,PaymentReconciliationClient.ProofRequest.class); assertEquals(f.request,p.operationId()); calls.incrementAndGet(); return proof; });
        jdbc.execute("CREATE FUNCTION test_fail_compensation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.status='COMPENSATED' THEN RAISE EXCEPTION 'simulated local commit failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER test_fail_compensation BEFORE UPDATE ON compensation_request FOR EACH ROW EXECUTE FUNCTION test_fail_compensation()");
        try { resolver.processOne(); assertEquals("REQUESTED",status(f)); }
        finally { jdbc.execute("DROP TRIGGER test_fail_compensation ON compensation_request"); jdbc.execute("DROP FUNCTION test_fail_compensation()"); }
        assertEquals("RESERVED",jdbc.queryForObject("SELECT state FROM inventory_allocation_intents WHERE operation_id=?",String.class,f.intent));
        jdbc.update("UPDATE compensation_request SET next_check_at=clock_timestamp()-interval '1 second' WHERE request_id=?",f.request);
        resolver.processOne(); assertEquals("COMPENSATED",status(f)); assertEquals(2,calls.get());
    }

    @Test void revisionChangeAfterPaymentPreventsFinalize() throws Exception {
        Fixture f=fixture();
        when(payment.verify(eq(f.id),any())).thenAnswer(invocation->{jdbc.update("UPDATE reconciliation_finding SET current_revision=2 WHERE order_id=?",f.id); return proof(f);});
        resolver.processOne();
        assertEquals("STALE",status(f));
        assertEquals("RESERVED",jdbc.queryForObject("SELECT state FROM inventory_allocation_intents WHERE operation_id=?",String.class,f.intent));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM compensation_event WHERE request_id=? AND outcome='COMPENSATED'",Integer.class,f.request));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM compensation_event WHERE request_id=? AND outcome='STALE'",Integer.class,f.request));
    }

    @Test void changedSnapshotAfterPaymentRecordsStaleAndAllowsNewRequest() throws Exception {
        Fixture f=fixture();
        when(payment.verify(eq(f.id),any())).thenAnswer(invocation->{jdbc.update("UPDATE orders SET status='ON_HOLD' WHERE id=?",f.id); return proof(f);});
        resolver.processOne(); assertEquals("STALE",status(f));
        assertEquals("RESERVED",jdbc.queryForObject("SELECT state FROM inventory_allocation_intents WHERE operation_id=?",String.class,f.intent));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM compensation_event WHERE request_id=? AND outcome='STALE'",Integer.class,f.request));
        assertEquals("STALE",jdbc.queryForObject("SELECT compensation_submit(?,?,1,'review')",String.class,f.request,f.id));
        assertEquals("REQUESTED",jdbc.queryForObject("SELECT compensation_submit(?,?,1,'new review')",String.class,UUID.randomUUID(),f.id));
    }

    @Test void oppositeLocalTerminalAfterCatalogObservationRemainsUnchangedAndStale() throws Exception {
        Fixture f=fixture();
        when(payment.verify(eq(f.id),any())).thenAnswer(invocation->{
            jdbc.update("UPDATE inventory_allocation_intents SET state='REJECTED' WHERE operation_id=?",f.intent);
            return proof(f);
        });
        resolver.processOne();
        assertEquals("STALE",status(f));
        assertEquals("REJECTED",jdbc.queryForObject("SELECT state FROM inventory_allocation_intents WHERE operation_id=?",String.class,f.intent));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM compensation_event WHERE request_id=? AND outcome='STALE'",Integer.class,f.request));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM compensation_event WHERE request_id=? AND outcome='COMPENSATED'",Integer.class,f.request));
        assertEquals("STALE",jdbc.queryForObject("SELECT compensation_submit(?,?,1,'review')",String.class,f.request,f.id));
        verify(catalog).lookup(f.intent);
        verifyNoMoreInteractions(catalog);
    }

    @Test void paymentOperationConflictAfterStaleBecomesHold() throws Exception {
        Fixture f=fixture(); var oldProof=proof(f);
        when(payment.verify(eq(f.id),any())).thenAnswer(invocation->{jdbc.update("UPDATE reconciliation_finding SET current_revision=2 WHERE order_id=?",f.id); return oldProof;});
        resolver.processOne(); assertEquals("STALE",status(f));
        jdbc.update("INSERT INTO reconciliation_revision(order_id,revision,evidence,reasons) SELECT order_id,2,evidence,reasons FROM reconciliation_revision WHERE order_id=? AND revision=1",f.id);
        UUID next=UUID.randomUUID(); jdbc.queryForObject("SELECT compensation_submit(?,?,2,'new review')",String.class,next,f.id);
        var response=feign.Response.builder().status(409).reason("conflict").request(feign.Request.create(feign.Request.HttpMethod.POST,"http://fixture",java.util.Map.of(),null,java.nio.charset.StandardCharsets.UTF_8,null)).build();
        when(payment.verify(eq(f.id),any())).thenThrow(feign.FeignException.errorStatus("verify",response));
        resolver.processOne();
        assertEquals("HOLD",jdbc.queryForObject("SELECT status FROM compensation_request WHERE request_id=?",String.class,next));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM compensation_event WHERE request_id=? AND action='PAYMENT_OPERATION_CONFLICT' AND outcome='HOLD'",Integer.class,next));
    }

    @Test void expiredClaimAfterPaymentPreventsFinalize() throws Exception {
        Fixture f=fixture();
        when(payment.verify(eq(f.id),any())).thenAnswer(invocation->{jdbc.update("UPDATE compensation_request SET claim_until=clock_timestamp()-interval '1 second' WHERE request_id=?",f.request); return proof(f);});
        resolver.processOne();
        assertNotEquals("COMPENSATED",status(f));
        assertEquals("RESERVED",jdbc.queryForObject("SELECT state FROM inventory_allocation_intents WHERE operation_id=?",String.class,f.intent));
    }

    @Test void unsupportedCheckoutSnapshotIsHeld() throws Exception {
        Fixture f=fixture();
        jdbc.update("UPDATE checkout_attempts SET request_snapshot='{\"userId\":\"other\",\"currency\":\"USD\",\"paymentMethod\":\"CARD\"}' WHERE attempt_key=(SELECT checkout_key FROM orders WHERE id=?)",f.id);
        resolver.processOne();
        assertEquals("HOLD",status(f));
        verifyNoInteractions(payment,catalog);
    }

    @Test void invalidProofBindingsAreHeld() throws Exception {
        for (int mode=0;mode<4;mode++) {
            Fixture f=fixture(); var p=proof(f);
            var invalid=new PaymentReconciliationClient.ProofResponse(p.orderId(),p.operationId(),mode==1?2:p.findingRevision(),p.status(),p.reason(),p.proofId(),p.financialVersion(),mode==2?null:p.proofVersion(),mode==3?Instant.now().plusSeconds(3600):p.observedAt(),mode==0?10L:p.paymentId(),p.paymentNumber(),p.amount(),p.currency(),p.closeOperationId(),p.evidence());
            when(payment.verify(eq(f.id),any())).thenReturn(invalid);
            resolver.processOne(); assertEquals("HOLD",status(f));
            assertEquals("RESERVED",jdbc.queryForObject("SELECT state FROM inventory_allocation_intents WHERE operation_id=?",String.class,f.intent));
        }
    }

    @Test void paymentVersionChangeReopensHoldWithoutErasingProof() throws Exception {
        Fixture f=fixture(); var p=proof(f); when(payment.verify(eq(f.id),any())).thenReturn(p);
        resolver.processOne(); assertEquals("COMPENSATED",status(f));
        jdbc.update("UPDATE compensation_request SET next_check_at=clock_timestamp()-interval '1 second' WHERE request_id=?",f.request);
        var changed=new PaymentReconciliationClient.ProofResponse(p.orderId(),p.operationId(),p.findingRevision(),p.status(),p.reason(),p.proofId(),4,p.proofVersion(),p.observedAt(),p.paymentId(),p.paymentNumber(),p.amount(),p.currency(),p.closeOperationId(),p.evidence());
        when(payment.proof(f.id,f.request)).thenReturn(changed);
        resolver.processOne(); assertEquals("HOLD",status(f));
        assertEquals(p.proofId(),jdbc.queryForObject("SELECT proof_id FROM compensation_request WHERE request_id=?",UUID.class,f.request));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM compensation_event WHERE request_id=? AND outcome='COMPENSATED'",Integer.class,f.request));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM compensation_event WHERE request_id=? AND outcome='HOLD'",Integer.class,f.request));
    }

    @Test void malformedSnapshotHeldRatherThanThrowing() throws Exception {
        Fixture f=fixture(); jdbc.update("UPDATE checkout_attempts SET request_snapshot='not json' WHERE attempt_key=(SELECT checkout_key FROM orders WHERE id=?)",f.id);
        resolver.processOne(); assertEquals("HOLD",status(f));
    }

    private PaymentReconciliationClient.ProofResponse compensated(Fixture f) {
        var p=proof(f); when(payment.verify(eq(f.id),any())).thenReturn(p); resolver.processOne();
        assertEquals("COMPENSATED",status(f));
        jdbc.update("UPDATE compensation_request SET next_check_at=clock_timestamp()-interval '1 second' WHERE request_id=?",f.request);
        when(payment.proof(f.id,f.request)).thenReturn(p);
        return p;
    }

    @Test void paymentHold404AndNetworkFailurePreserveProofHistory() throws Exception {
        for(int mode=0;mode<3;mode++) {
            Fixture f=fixture(); var p=compensated(f);
            if(mode==0) when(payment.proof(f.id,f.request)).thenReturn(new PaymentReconciliationClient.ProofResponse(p.orderId(),p.operationId(),p.findingRevision(),"HOLD","late",p.proofId(),p.financialVersion(),p.proofVersion(),p.observedAt(),p.paymentId(),p.paymentNumber(),p.amount(),p.currency(),p.closeOperationId(),p.evidence()));
            else if(mode==1) when(payment.proof(f.id,f.request)).thenThrow(feign.FeignException.NotFound.class);
            else when(payment.proof(f.id,f.request)).thenThrow(new IllegalStateException("network"));
            resolver.processOne(); assertEquals("HOLD",status(f));
            assertEquals(p.proofId(),jdbc.queryForObject("SELECT proof_id FROM compensation_request WHERE request_id=?",UUID.class,f.request));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM compensation_event WHERE request_id=? AND outcome='COMPENSATED'",Integer.class,f.request));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM compensation_event WHERE request_id=? AND outcome='HOLD'",Integer.class,f.request));
        }
    }

    @Test void inventoryAndLocalContradictionsReopenHoldWithoutRemoteMutation() throws Exception {
        for(int mode=0;mode<3;mode++) {
            Fixture f=fixture(); compensated(f);
            if(mode==0) when(catalog.lookup(f.intent)).thenReturn(new InventoryReservationClient.Reservation(f.intent,"PRODUCT",99L,1,"owner","RELEASED",null));
            if(mode==1) when(catalog.lookup(f.intent)).thenReturn(new InventoryReservationClient.Reservation(f.intent,"PRODUCT",42L,1,"owner","REJECTED",null));
            if(mode==2) jdbc.update("UPDATE orders SET accepted_payment_id=9,accepted_payment_number='pay' WHERE id=?",f.id);
            resolver.processOne(); assertEquals("HOLD",status(f));
        }
        verify(catalog,never()).reserve(any()); verify(catalog,never()).release(any()); verify(catalog,never()).fulfill(any());
    }

    @Test void detectionRevisionAloneDoesNotInvalidateProof() throws Exception {
        Fixture f=fixture(); compensated(f);
        jdbc.update("UPDATE reconciliation_finding SET current_revision=2 WHERE order_id=?",f.id);
        resolver.processOne(); assertEquals("COMPENSATED",status(f));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM compensation_event WHERE request_id=? AND outcome='HOLD'",Integer.class,f.request));
        assertTrue(jdbc.queryForObject("SELECT next_check_at>clock_timestamp() FROM compensation_request WHERE request_id=?",Boolean.class,f.request));
    }

    @Test void recurrenceLeaseTakeoverRejectsExpiredToken() throws Exception {
        Fixture f=fixture(); compensated(f);
        var old=store.claim(Duration.ofMinutes(1)); assertEquals(f.request,old.requestId());
        jdbc.update("UPDATE compensation_request SET claim_until=clock_timestamp()-interval '1 second',next_check_at=clock_timestamp()-interval '1 second' WHERE request_id=?",f.request);
        var reconstructed=new CompensationStore(jdbc); var fresh=reconstructed.claim(Duration.ofMinutes(1));
        assertNotEquals(old.token(),fresh.token()); assertFalse(store.recurrence(old,"OLD")); assertTrue(reconstructed.recurrence(fresh,null));
    }

    @Test void durableDueQueueFairAcrossMoreThanTwoBatchesAndNewRequests() throws Exception {
        Set<UUID> expected=new HashSet<>();
        for(int i=0;i<101;i++) {
            Fixture f=fixture(); expected.add(f.request);
            jdbc.update("UPDATE compensation_request SET status='COMPENSATED',proof_id=?,proof_version=1,proof_observed_at=clock_timestamp(),proof_snapshot='{}',compensated_snapshot='{}',next_check_at=clock_timestamp()-interval '10 minutes'+(? * interval '1 millisecond') WHERE request_id=?",UUID.randomUUID(),i,f.request);
        }
        var recreated=new CompensationStore(jdbc);
        for(int i=0;i<101;i++) {
            if(i%20==0) { Fixture extra=fixture(); jdbc.update("UPDATE compensation_request SET next_check_at=clock_timestamp() WHERE request_id=?",extra.request); }
            var claim=recreated.claim(Duration.ofMinutes(1)); assertNotNull(claim); assertTrue(expected.remove(claim.requestId()));
            // Model observation failure: the persisted HOLD removes only the failed obligation from polling.
            assertTrue(recreated.recurrence(claim,"OBSERVATION_FAILED"));
        }
        assertTrue(expected.isEmpty());
        var initial=recreated.claim(Duration.ofMinutes(1)); assertNotNull(initial); assertFalse(initial.recurrence());
        assertTrue(recreated.hold(initial,"TEST"));
    }

    @Test void processOneVisitsAllOldObligationsAndNewInitialRequests() throws Exception {
        var originals=new ArrayList<Fixture>();
        var proofs=new HashMap<UUID,PaymentReconciliationClient.ProofResponse>();
        var visited=new HashSet<UUID>();
        for(int i=0;i<101;i++) {
            Fixture f=fixture(); originals.add(f);
            var p=proof(f); proofs.put(f.request,p);
            jdbc.update("UPDATE inventory_allocation_intents SET state='RELEASED' WHERE operation_id=?",f.intent);
            jdbc.update("UPDATE compensation_request SET status='COMPENSATED',proof_id=?,proof_version=?,proof_observed_at=?,proof_snapshot=CAST(? AS jsonb),compensated_snapshot=compensation_local_snapshot(?),next_check_at='2020-01-01T00:00:00Z'::timestamptz+(? * interval '1 millisecond') WHERE request_id=?",
                    p.proofId(),p.proofVersion(),java.sql.Timestamp.from(p.observedAt()),mapper.writeValueAsString(p),f.id,i,f.request);
            jdbc.update("INSERT INTO compensation_event(request_id,actor,db_principal,executor_login,action,outcome,before_state,after_state) VALUES (?,'fixture',current_user,current_user,'VERIFY_EXTERNAL_REFUND','COMPENSATED','{}',jsonb_build_object('proofId',?::text))",f.request,p.proofId());
        }
        Set<UUID> failures=Set.of(originals.get(0).request,originals.get(1).request,originals.get(2).request);
        when(payment.proof(anyLong(),any())).thenAnswer(invocation->{
            UUID operation=invocation.getArgument(1); visited.add(operation);
            if(failures.contains(operation)) throw new IllegalStateException("fixture timeout");
            return proofs.get(operation);
        });
        var newRequests=new ArrayList<Fixture>();
        int calls=0;
        for(;calls<101;calls++) {
            if(calls%10==0) {
                Fixture f=fixture(); newRequests.add(f);
                jdbc.update("UPDATE checkout_attempts SET request_snapshot='not json' WHERE attempt_key=(SELECT checkout_key FROM orders WHERE id=?)",f.id);
            }
            resolver.processOne();
        }
        for(int i=0;i<newRequests.size();i++) { resolver.processOne(); calls++; }
        assertTrue(calls<=3*101+20);
        assertEquals(proofs.keySet(),visited);
        for(Fixture f:originals) {
            assertEquals(failures.contains(f.request)?"HOLD":"COMPENSATED",status(f));
            assertEquals(proofs.get(f.request).proofId(),jdbc.queryForObject("SELECT proof_id FROM compensation_request WHERE request_id=?",UUID.class,f.request));
            assertEquals(proofs.get(f.request).proofId().toString(),jdbc.queryForObject("SELECT after_state->>'proofId' FROM compensation_event WHERE request_id=? AND outcome='COMPENSATED'",String.class,f.request));
            if(failures.contains(f.request)) assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM compensation_event WHERE request_id=? AND outcome='HOLD'",Integer.class,f.request));
            else assertTrue(jdbc.queryForObject("SELECT next_check_at>clock_timestamp() FROM compensation_request WHERE request_id=?",Boolean.class,f.request));
        }
        for(Fixture f:newRequests) assertEquals("HOLD",status(f));
        verify(catalog,never()).reserve(any()); verify(catalog,never()).release(any()); verify(catalog,never()).fulfill(any());
    }
}
