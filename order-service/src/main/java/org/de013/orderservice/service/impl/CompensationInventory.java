package org.de013.orderservice.service.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.de013.orderservice.client.InventoryReservationClient.Reservation;

/** Validates a complete observation before any local intent state is synchronized. */
final class CompensationInventory {
    record Line(String productId, int quantity) { }
    record Intent(UUID operationId, String targetKind, Long targetId, int quantity, String owner, String state) { }
    record Observation(Intent intent, Reservation reservation) { }
    record Update(UUID operationId, String before, String after) { }
    record Result(String reason, List<Update> updates) { }

    private CompensationInventory() { }

    static Result validate(String owner, List<Line> lines, List<Observation> observations) {
        if (owner == null || owner.isBlank() || lines == null || lines.isEmpty()
                || observations == null || observations.isEmpty()) return hold("INCOMPLETE_INVENTORY");
        Map<String, Long> expected = new HashMap<>();
        for (Line line : lines) {
            if (line == null || line.productId() == null || !line.productId().matches("[1-9][0-9]*") || line.quantity() <= 0)
                return hold("INCOMPLETE_ORDER_LINES");
            expected.merge(line.productId(), (long) line.quantity(), Long::sum);
        }
        Map<String, Long> actual = new HashMap<>();
        Set<UUID> seen = new HashSet<>();
        var updates = new java.util.ArrayList<Update>();
        for (Observation observation : observations) {
            if (observation == null || observation.intent() == null || observation.reservation() == null)
                return hold("INCOMPLETE_INVENTORY");
            Intent i = observation.intent();
            Reservation r = observation.reservation();
            if (!"PRODUCT".equals(i.targetKind())) return hold("VARIANT_PROVENANCE_UNPROVEN");
            if (i.operationId() == null || !seen.add(i.operationId()) || i.targetId() == null || i.targetId() <= 0
                    || i.quantity() <= 0 || !owner.equals(i.owner()) || !i.operationId().equals(r.operationId())
                    || !i.targetKind().equals(r.targetKind()) || !i.targetId().equals(r.targetId())
                    || !Objects.equals(i.quantity(), r.quantity()) || !owner.equals(r.owner())) return hold("INVENTORY_IDENTITY_MISMATCH");
            if (!Set.of("RELEASED", "REJECTED").contains(Objects.toString(r.state(), ""))) return hold("INVENTORY_NOT_TERMINAL");
            String state = Objects.toString(i.state(), "");
            if (!(state.equals(r.state()) || state.equals("PENDING")
                    || state.equals("RESERVED") && r.state().equals("RELEASED"))) return hold("INVENTORY_STATE_MISMATCH");
            actual.merge(i.targetId().toString(), (long) i.quantity(), Long::sum);
            if (!state.equals(r.state())) updates.add(new Update(i.operationId(), state, r.state()));
        }
        if (!expected.equals(actual)) return hold("INVENTORY_SET_MISMATCH");
        return new Result(null, List.copyOf(updates));
    }

    private static Result hold(String reason) { return new Result(reason, List.of()); }
}
