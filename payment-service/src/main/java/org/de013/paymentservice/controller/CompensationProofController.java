package org.de013.paymentservice.controller;

import java.util.UUID;
import org.de013.paymentservice.service.CompensationProofService;
import org.de013.paymentservice.service.CompensationProofStore;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/reconciliation/orders/{orderId}/compensation-proofs")
public class CompensationProofController {
    private final CompensationProofService service;
    private final CompensationProofStore store;

    public CompensationProofController(CompensationProofService service, CompensationProofStore store) {
        this.service = service; this.store = store;
    }

    @PostMapping
    public CompensationProofStore.View resolve(@PathVariable long orderId, @RequestBody CompensationProofStore.Request request) {
        return service.resolve(orderId, request);
    }

    @GetMapping("/{operationId}")
    public CompensationProofStore.View view(@PathVariable long orderId, @PathVariable UUID operationId) {
        return store.view(orderId, operationId);
    }
}
