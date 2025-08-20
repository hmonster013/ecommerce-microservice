package org.de013.orderservice.client;

import org.de013.orderservice.dto.integration.payment.PaymentDtos;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", path = "/api/v1/payments")
public interface PaymentServiceClient {

    @PostMapping("/authorize")
    PaymentDtos.AuthorizeResponse authorize(@RequestBody PaymentDtos.AuthorizeRequest request);

    @PostMapping("/capture")
    void capture(@RequestBody PaymentDtos.CaptureRequest request);

    @PostMapping("/refund")
    PaymentDtos.RefundResponse refund(@RequestBody PaymentDtos.RefundRequest request);
}

