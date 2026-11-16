package org.de013.orderservice.service.impl;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "payment-service", contextId = "paymentReconciliation", configuration = paymentreconciliationconfig.PaymentReconciliationFeignConfig.class)
interface PaymentReconciliationClient {
    @GetMapping("/internal/reconciliation/orders/{orderId}")
    ReconciliationClassifier.PaymentSnapshot read(@PathVariable("orderId") long orderId);
}
