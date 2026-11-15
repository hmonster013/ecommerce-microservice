package org.de013.productcatalog.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.de013.productcatalog.dto.inventory.ReservationRequest;
import org.de013.productcatalog.dto.inventory.ReservationResponse;
import org.de013.productcatalog.exception.ReservationException;
import org.de013.productcatalog.service.support.InventoryCacheInvalidator;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryReservationService {
    private final JdbcTemplate jdbc;
    private final InventoryCacheInvalidator cache;

    @Transactional
    public ReservationResponse reserve(ReservationRequest request) {
        int inserted = jdbc.update("""
                INSERT INTO inventory_reservations (operation_id, target_kind, target_id, quantity, owner, state)
                VALUES (?, ?, ?, ?, ?, 'RESERVED') ON CONFLICT (operation_id) DO NOTHING
                """, request.operationId(), request.targetKind().name(), request.targetId(), request.quantity(), request.owner());
        ReservationResponse current = find(request.operationId(), true);
        if (!current.targetKind().equals(request.targetKind()) || !current.targetId().equals(request.targetId())
                || !current.quantity().equals(request.quantity()) || !current.owner().equals(request.owner())) {
            throw conflict();
        }
        if (inserted == 0) {
            if (current.state().equals("RESERVED") || current.state().equals("REJECTED")) return current;
            throw conflict();
        }
        List<int[]> stock = lockStock(current);
        String reason = stock.isEmpty() ? "TARGET_NOT_FOUND"
                : stock.get(0)[0] - stock.get(0)[1] < current.quantity() ? "INSUFFICIENT_STOCK" : null;
        if (reason != null) {
            jdbc.update("UPDATE inventory_reservations SET state='REJECTED', reason=? WHERE operation_id=?", reason, current.operationId());
        } else {
            updateStock(current, 0, current.quantity());
        }
        return find(current.operationId(), false);
    }

    @Transactional(readOnly = true)
    public ReservationResponse lookup(UUID id) { return find(id, false); }

    @Transactional
    public ReservationResponse terminal(UUID id, boolean fulfill) {
        ReservationResponse current = find(id, true);
        String terminal = fulfill ? "FULFILLED" : "RELEASED";
        if (current.state().equals(terminal)) return current;
        if (!current.state().equals("RESERVED")) throw conflict();
        if (lockStock(current).isEmpty()) throw new IllegalStateException("Reserved inventory is missing");
        updateStock(current, fulfill ? -current.quantity() : 0, -current.quantity());
        jdbc.update("UPDATE inventory_reservations SET state=? WHERE operation_id=?", terminal, id);
        return find(id, false);
    }

    private ReservationResponse find(UUID id, boolean lock) {
        List<ReservationResponse> rows = jdbc.query("SELECT * FROM inventory_reservations WHERE operation_id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> new ReservationResponse(rs.getObject("operation_id", UUID.class),
                        ReservationRequest.TargetKind.valueOf(rs.getString("target_kind")), rs.getLong("target_id"),
                        rs.getInt("quantity"), rs.getString("owner"), rs.getString("state"), rs.getString("reason")), id);
        if (rows.isEmpty()) throw new ReservationException("OPERATION_NOT_FOUND", HttpStatus.NOT_FOUND);
        return rows.get(0);
    }

    private List<int[]> lockStock(ReservationResponse reservation) {
        return jdbc.query("SELECT quantity, reserved_quantity FROM " + table(reservation) + " WHERE " + key(reservation) + "=? FOR UPDATE",
                (rs, n) -> new int[]{rs.getInt(1), rs.getInt(2)}, reservation.targetId());
    }

    private void updateStock(ReservationResponse reservation, int quantity, int reserved) {
        jdbc.update("UPDATE " + table(reservation) + " SET quantity=quantity+?, reserved_quantity=reserved_quantity+?, updated_at=CURRENT_TIMESTAMP WHERE " + key(reservation) + "=?",
                quantity, reserved, reservation.targetId());
        if (reservation.targetKind() == ReservationRequest.TargetKind.PRODUCT) cache.productChanged(reservation.targetId());
        else cache.variantChanged(reservation.targetId());
    }

    private String table(ReservationResponse r) { return r.targetKind() == ReservationRequest.TargetKind.PRODUCT ? "inventory" : "variant_inventory"; }
    private String key(ReservationResponse r) { return r.targetKind() == ReservationRequest.TargetKind.PRODUCT ? "product_id" : "variant_id"; }
    private ReservationException conflict() { return new ReservationException("RESERVATION_CONFLICT", HttpStatus.CONFLICT); }
}
