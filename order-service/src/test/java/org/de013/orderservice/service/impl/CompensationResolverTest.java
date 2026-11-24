package org.de013.orderservice.service.impl;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.de013.orderservice.client.InventoryReservationClient;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CompensationResolverTest {
    @Test void verifiedProofFinalizesMatchingTerminalIntent() throws Exception {
        var store=mock(CompensationStore.class); var jdbc=mock(JdbcTemplate.class);
        var payment=mock(PaymentReconciliationClient.class); var catalog=mock(InventoryReservationClient.class);
        var mapper=new ObjectMapper().findAndRegisterModules(); UUID request=UUID.randomUUID(), close=UUID.randomUUID(), operation=UUID.randomUUID();
        String local="""
                {"order":{"id":1,"userId":"owner","status":"CANCELLED","allocationPhase":"CLOSED","checkoutKey":"key",
                "closeOperation":"%s","actualDeliveryDate":null,"deletedAt":null,"amount":12.50,"currency":"USD"},
                "checkout":{"userId":"owner","requestUserId":"owner","requestSnapshotHash":"hash","requestCurrency":"USD","requestPaymentMethod":"CARD"},
                "items":[{"productId":"42","quantity":1,"actualDeliveryDate":null,"deletedAt":null}],
                "intents":[{"operationId":"%s","targetKind":"PRODUCT","targetId":42,"quantity":1,"owner":"owner","state":"RESERVED"}]}
                """.formatted(close,operation);
        var claim=new CompensationStore.Claim(request,1,2,UUID.randomUUID(),local);
        when(store.claim(any())).thenReturn(claim);
        var attempt=new ReconciliationClassifier.Attempt(9L,"pay","SUCCEEDED",new BigDecimal("12.50"),"USD","STRIPE",true);
        var snapshot=new ReconciliationClassifier.PaymentSnapshot(1L,Instant.now(),true,true,close,"HOLD","LATE",null,false,List.of(attempt));
        when(jdbc.queryForObject(anyString(),eq(String.class),eq(1L),eq(2L))).thenReturn(mapper.writeValueAsString(java.util.Map.of("payment",snapshot)));
        when(payment.read(1)).thenReturn(snapshot);
        when(catalog.lookup(operation)).thenReturn(new InventoryReservationClient.Reservation(operation,"PRODUCT",42L,1,"owner","RELEASED",null));
        when(payment.verify(eq(1L),any())).thenReturn(new PaymentReconciliationClient.ProofResponse(1,request,2,"COMPENSATED","REFUNDED",UUID.randomUUID(),3,3L,Instant.now(),9L,"pay",new BigDecimal("12.50"),"USD",close,mapper.createObjectNode()));
        new CompensationResolver(store,jdbc,payment,catalog,mapper).processOne();
        verify(store).finish(eq(claim),contains("COMPENSATED"),contains("RELEASED"));
        verify(store,never()).retry(any(),any(Duration.class));
    }
}
