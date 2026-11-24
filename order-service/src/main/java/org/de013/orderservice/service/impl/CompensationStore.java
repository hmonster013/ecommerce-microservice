package org.de013.orderservice.service.impl;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class CompensationStore {
    record Claim(UUID requestId, long orderId, long revision, UUID token, String localSnapshot, boolean recurrence, String proofSnapshot, String compensatedSnapshot) {
        Claim(UUID id,long order,long revision,UUID token,String snapshot) { this(id,order,revision,token,snapshot,false,null,null); }
    }
    private final JdbcTemplate jdbc;
    CompensationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Claim claim(Duration lease) {
        if (lease == null || lease.toMillis() <= 0 || lease.toMillis() > 900000) throw new IllegalArgumentException("invalid lease");
        List<Claim> rows = jdbc.query("SELECT * FROM compensation_claim(?)", (rs, n) -> new Claim(
                rs.getObject(1, UUID.class), rs.getLong(2), rs.getLong(3), rs.getObject(4, UUID.class), rs.getString(5),rs.getBoolean(6),rs.getString(7),rs.getString(8)), lease.toMillis());
        return rows.isEmpty() ? null : rows.get(0);
    }

    boolean hold(Claim claim, String reason) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT compensation_hold(?,?,?)", Boolean.class,
                claim.requestId(), claim.token(), reason));
    }

    void retry(Claim claim, Duration delay) {
        jdbc.queryForObject("SELECT compensation_retry(?,?,?)", Boolean.class, claim.requestId(), claim.token(), delay.toMillis());
    }

    boolean finish(Claim claim, String proof, String updates) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT compensation_finalize(?,?,CAST(? AS jsonb),CAST(? AS jsonb))", Boolean.class,
                claim.requestId(), claim.token(), proof, updates));
    }

    boolean recurrence(Claim claim, String reason) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT compensation_recurrence(?,?,?,?)",Boolean.class,
                claim.requestId(),claim.token(),reason,Duration.ofMinutes(5).toMillis()));
    }
}
