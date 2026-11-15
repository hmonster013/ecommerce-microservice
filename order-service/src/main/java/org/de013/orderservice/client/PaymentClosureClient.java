package org.de013.orderservice.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", contextId = "paymentClosure", path = "/internal/checkout-closures", configuration = paymentclosureconfig.PaymentClosureFeignConfig.class)
public interface PaymentClosureClient {
    record Request(UUID operationId) { }
    record Result(Long orderId, UUID operationId, String status, String reason) { }
    @PostMapping("/{orderId}") Result close(@PathVariable("orderId") long orderId, @RequestBody Request request);
}
