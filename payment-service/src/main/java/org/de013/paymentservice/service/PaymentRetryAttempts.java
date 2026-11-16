package org.de013.paymentservice.service;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentRetryAttempts {
    private final JdbcTemplate jdbc;

    public PaymentRetryAttempts(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Attempt(long id, UUID providerKey, String state, boolean created) { }

    /** Must be invoked without holding the payment order guard. Existing attempts never authorize a resend. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Attempt claim(long paymentId, String key, String method, BigDecimal amount, String currency) {
        if (paymentId <= 0 || key == null || !key.matches("[A-Za-z0-9._:-]{1,128}") || method == null
                || !method.matches("[A-Za-z0-9_]{1,255}") || amount == null || amount.signum() <= 0
                || currency == null || !currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Invalid retry identity");
        UUID providerKey = UUID.randomUUID();
        int inserted = jdbc.update("INSERT INTO payment_retry_attempt(payment_id,idempotency_key,payment_method_id,amount,currency,provider_key) VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                paymentId, key, method, amount, currency, providerKey);
        var rows = jdbc.query("SELECT id,provider_key,state,payment_method_id,amount,currency FROM payment_retry_attempt WHERE payment_id=? AND idempotency_key=?",
                (rs, n) -> new Row(rs.getLong(1), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getString(6)), paymentId, key);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Another retry attempt is active");
        Row row = rows.get(0);
        if (!method.equals(row.method()) || amount.compareTo(row.amount()) != 0 || !currency.equals(row.currency()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key payload mismatch");
        return new Attempt(row.id(), row.providerKey(), row.state(), inserted == 1);
    }

    public Attempt existing(long paymentId, String key, String method, BigDecimal amount, String currency) {
        var rows = jdbc.query("SELECT id,provider_key,state,payment_method_id,amount,currency FROM payment_retry_attempt WHERE payment_id=? AND idempotency_key=?",
                (rs, n) -> new Row(rs.getLong(1), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getString(6)), paymentId, key);
        if (rows.isEmpty()) return null;
        Row row = rows.get(0);
        if (!method.equals(row.method()) || amount.compareTo(row.amount()) != 0 || !currency.equals(row.currency()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key payload mismatch");
        return new Attempt(row.id(), row.providerKey(), row.state(), false);
    }

    public boolean active(long paymentId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM payment_retry_attempt WHERE payment_id=? AND state IN ('RESERVED','UNCERTAIN'))", Boolean.class, paymentId));
    }

    public boolean exists(long paymentId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM payment_retry_attempt WHERE payment_id=?)", Boolean.class, paymentId));
    }

    public boolean hosted(long paymentId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM hosted_checkouts WHERE payment_id=?)", Boolean.class, paymentId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void completed(long id) {
        jdbc.update("UPDATE payment_retry_attempt SET state='COMPLETED' WHERE id=?", id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejected(long id) {
        jdbc.update("UPDATE payment_retry_attempt SET state='REJECTED' WHERE id=? AND state='RESERVED'", id);
    }

    private record Row(long id, UUID providerKey, String state, String method, BigDecimal amount, String currency) { }
}
