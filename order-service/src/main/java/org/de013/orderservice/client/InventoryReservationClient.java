package org.de013.orderservice.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "product-catalog-service", contextId = "inventoryReservation", path = "/internal/inventory-reservations", configuration = inventoryconfig.InventoryFeignConfig.class)
public interface InventoryReservationClient {
    record Reservation(UUID operationId, String targetKind, Long targetId, Integer quantity, String owner, String state, String reason) { }
    record Request(UUID operationId, String targetKind, Long targetId, Integer quantity, String owner) { }
    @PostMapping Reservation reserve(@RequestBody Request request);
    @GetMapping("/{id}") Reservation lookup(@PathVariable("id") UUID id);
    @PostMapping("/{id}/release") Reservation release(@PathVariable("id") UUID id);
    @PostMapping("/{id}/fulfill") Reservation fulfill(@PathVariable("id") UUID id);
}
