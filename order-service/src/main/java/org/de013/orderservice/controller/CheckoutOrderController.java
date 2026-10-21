package org.de013.orderservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.de013.common.security.UserContextHolder;
import org.de013.orderservice.dto.request.CreateOrderRequest;
import org.de013.orderservice.dto.response.CheckoutOrderResponse;
import org.de013.orderservice.service.impl.CheckoutOrderService;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/orders/checkout")
@RequiredArgsConstructor
public class CheckoutOrderController {
    private final CheckoutOrderService checkout;

    @PostMapping("/{key}")
    public CheckoutOrderResponse create(@PathVariable UUID key, @Valid @RequestBody CreateOrderRequest request) {
        return checkout.create(key, UserContextHolder.requireAuthenticated().getUserId(), request);
    }

    @GetMapping("/{key}")
    public CheckoutOrderResponse read(@PathVariable UUID key) {
        return checkout.read(key, UserContextHolder.requireAuthenticated().getUserId());
    }
}
