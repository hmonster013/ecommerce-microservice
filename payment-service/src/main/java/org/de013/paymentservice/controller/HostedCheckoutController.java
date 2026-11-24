package org.de013.paymentservice.controller;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.de013.common.controller.BaseController;
import org.de013.common.dto.ApiResult;
import org.de013.common.security.UserContextHolder;
import org.de013.paymentservice.dto.payment.HostedCheckoutResponse;
import org.de013.paymentservice.service.HostedCheckoutService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments/checkout")
@RequiredArgsConstructor
@Validated
public class HostedCheckoutController extends BaseController {
    private final HostedCheckoutService checkout;

    @PostMapping("/{orderId}")
    public ResponseEntity<ApiResult<HostedCheckoutResponse>> start(@PathVariable @Positive Long orderId) {
        return success(checkout.start(orderId, UserContextHolder.requireAuthenticated().getUserId()));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResult<HostedCheckoutResponse>> read(@PathVariable @Positive Long orderId) {
        return success(checkout.read(orderId, UserContextHolder.requireAuthenticated().getUserId()));
    }
}
