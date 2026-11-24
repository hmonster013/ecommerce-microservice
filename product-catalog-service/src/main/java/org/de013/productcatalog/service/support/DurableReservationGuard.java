package org.de013.productcatalog.service.support;

import org.de013.productcatalog.exception.ReservationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DurableReservationGuard {
    private final JdbcTemplate jdbc;

    public DurableReservationGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void rejectLegacyMutation(boolean variant, Long id) {
        String table = variant ? "variant_inventory" : "inventory";
        String key = variant ? "variant_id" : "product_id";
        jdbc.query("SELECT id FROM " + table + " WHERE " + key + "=? FOR UPDATE", rs -> {}, id);
        Boolean active = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM inventory_reservations WHERE target_kind=? AND target_id=? AND state='RESERVED')",
                Boolean.class, variant ? "VARIANT" : "PRODUCT", id);
        if (Boolean.TRUE.equals(active)) throw new ReservationException("RESERVATION_CONFLICT", HttpStatus.CONFLICT);
    }
}
