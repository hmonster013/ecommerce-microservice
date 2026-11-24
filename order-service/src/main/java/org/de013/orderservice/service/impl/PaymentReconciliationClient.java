package org.de013.orderservice.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", contextId = "paymentReconciliation", configuration = paymentreconciliationconfig.PaymentReconciliationFeignConfig.class, fallbackFactory = org.de013.orderservice.config.FeignResilienceConfig.RethrowFallbackFactory.class)
interface PaymentReconciliationClient {
    record ProofRequest(UUID operationId, long findingRevision, long expectedPaymentId, String expectedPaymentNumber,
                        BigDecimal expectedAmount, String expectedCurrency, UUID expectedCloseOperationId) { }
    record ProofResponse(long orderId, UUID operationId, long findingRevision, String status, String reason,
                         UUID proofId, long financialVersion, Long proofVersion, Instant observedAt, Long paymentId,
                         String paymentNumber, BigDecimal amount, String currency, UUID closeOperationId, JsonNode evidence) { }

    @GetMapping("/internal/reconciliation/orders/{orderId}")
    ReconciliationClassifier.PaymentSnapshot read(@PathVariable("orderId") long orderId);

    @PostMapping("/internal/reconciliation/orders/{orderId}/compensation-proofs")
    ProofResponse verify(@PathVariable("orderId") long orderId, @RequestBody ProofRequest request);

    @GetMapping("/internal/reconciliation/orders/{orderId}/compensation-proofs/{operationId}")
    ProofResponse proof(@PathVariable("orderId") long orderId, @PathVariable("operationId") UUID operationId);
}
