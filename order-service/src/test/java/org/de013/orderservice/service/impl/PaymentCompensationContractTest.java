package org.de013.orderservice.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentCompensationContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test void requestAndResponseUseWireV1FieldNames() throws Exception {
        UUID operation = UUID.randomUUID(), close = UUID.randomUUID(), proof = UUID.randomUUID();
        var request = new PaymentReconciliationClient.ProofRequest(operation, 3, 8, "payment", new BigDecimal("12.50"), "USD", close);
        var json = mapper.readTree(mapper.writeValueAsBytes(request));
        assertEquals(7, json.size());
        assertEquals(operation.toString(), json.path("operationId").asText());
        assertEquals(close.toString(), json.path("expectedCloseOperationId").asText());
        String response = """
                {"orderId":1,"operationId":"%s","findingRevision":3,"status":"COMPENSATED","reason":"REFUNDED",
                 "proofId":"%s","financialVersion":4,"proofVersion":4,"observedAt":"2026-01-01T00:00:00Z",
                 "paymentId":8,"paymentNumber":"payment","amount":12.50,"currency":"USD","closeOperationId":"%s","evidence":{}}
                """.formatted(operation, proof, close);
        var actual = mapper.readValue(response, PaymentReconciliationClient.ProofResponse.class);
        assertEquals(proof, actual.proofId());
        assertEquals(4L, actual.proofVersion());
        assertEquals(close, actual.closeOperationId());
        assertEquals(0, actual.amount().compareTo(new BigDecimal("12.50")));
    }
}
