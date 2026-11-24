package org.de013.productcatalog.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.de013.productcatalog.dto.inventory.ReservationRequest;
import org.de013.productcatalog.dto.inventory.ReservationResponse;
import org.de013.productcatalog.service.InventoryReservationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/inventory-reservations")
@RequiredArgsConstructor
public class InventoryReservationController {
    private final InventoryReservationService service;

    @PostMapping
    public ReservationResponse reserve(@Valid @RequestBody ReservationRequest request) { return service.reserve(request); }
    @GetMapping("/{operationId}")
    public ReservationResponse lookup(@PathVariable UUID operationId) { return service.lookup(operationId); }
    @PostMapping("/{operationId}/release")
    public ReservationResponse release(@PathVariable UUID operationId) { return service.terminal(operationId, false); }
    @PostMapping("/{operationId}/fulfill")
    public ReservationResponse fulfill(@PathVariable UUID operationId) { return service.terminal(operationId, true); }
}
