package org.de013.orderservice.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.de013.orderservice.client.InventoryReservationClient.Reservation;
import org.junit.jupiter.api.Test;

class CompensationInventoryTest {
    private final UUID operation = UUID.randomUUID();
    private final List<CompensationInventory.Line> lines = List.of(new CompensationInventory.Line("42", 2));

    private CompensationInventory.Result check(String local, String remote, String kind) {
        var intent = new CompensationInventory.Intent(operation, kind, 42L, 2, "owner", local);
        var reservation = new Reservation(operation, kind, 42L, 2, "owner", remote, null);
        return CompensationInventory.validate("owner", lines, List.of(new CompensationInventory.Observation(intent, reservation)));
    }

    @Test void terminalTransitionMatrix() {
        for (String terminal : List.of("RELEASED", "REJECTED")) {
            assertNull(check("PENDING", terminal, "PRODUCT").reason());
            assertNull(check(terminal, terminal, "PRODUCT").reason());
        }
        assertNull(check("RESERVED", "RELEASED", "PRODUCT").reason());
        assertEquals("INVENTORY_STATE_MISMATCH", check("RESERVED", "REJECTED", "PRODUCT").reason());
        assertEquals("INVENTORY_STATE_MISMATCH", check("RELEASED", "REJECTED", "PRODUCT").reason());
        assertEquals("INVENTORY_STATE_MISMATCH", check("FULFILLED", "RELEASED", "PRODUCT").reason());
    }

    @Test void incompleteOrVariantNeverProvesCompletion() {
        assertEquals("INCOMPLETE_INVENTORY", CompensationInventory.validate("owner", lines, List.of()).reason());
        assertEquals("VARIANT_PROVENANCE_UNPROVEN", check("PENDING", "RELEASED", "VARIANT").reason());
        var intent = new CompensationInventory.Intent(operation, "PRODUCT", 42L, 1, "owner", "PENDING");
        var remote = new Reservation(operation, "PRODUCT", 42L, 1, "owner", "RELEASED", null);
        var result = CompensationInventory.validate("owner", lines, List.of(new CompensationInventory.Observation(intent, remote)));
        assertEquals("INVENTORY_SET_MISMATCH", result.reason());
        assertTrue(result.updates().isEmpty());
    }
}
