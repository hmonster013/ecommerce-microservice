package org.de013.paymentservice.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.de013.paymentservice.service.CheckoutClosureService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/checkout-closures")
public class CheckoutClosureController {
    private final CheckoutClosureService closures;
    public record Request(UUID operationId) { }

    @PostMapping("/{orderId}")
    public CheckoutClosureService.Result close(@PathVariable Long orderId, @RequestBody Request request) {
        if (request == null || request.operationId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "operationId is required");
        return closures.close(orderId, request.operationId());
    }
}
