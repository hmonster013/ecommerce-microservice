package org.de013.paymentservice.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class PaymentReservationMigrationTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");

    private Flyway flyway(String schema, String target) {
        var config = Flyway.configure().dataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())
                .schemas(schema).defaultSchema(schema);
        if (target != null) config.target(target);
        return config.load();
    }

    @Test void duplicatesAbortMigrationWithoutRewritingFinancialStates() throws Exception {
        flyway("duplicate_test", "10").migrate();
        try (var c = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()); var s = c.createStatement()) {
            s.execute("SET search_path TO duplicate_test");
            s.execute("INSERT INTO payments(payment_number,order_id,user_id,amount,currency,status,method) VALUES ('old-1',1,'u',1,'USD','FAILED','CARD'),('old-2',1,'u',1,'USD','CANCELED','CARD')");
            assertThrows(Exception.class, () -> flyway("duplicate_test", null).migrate());
            try (var rows = s.executeQuery("SELECT count(*) FROM payments WHERE status IN ('FAILED','CANCELED')")) {
                assertTrue(rows.next()); assertEquals(2, rows.getInt(1));
            }
        }
    }

    @Test void v13LeavesExistingFinancialValuesAndSnapshotNull() throws Exception {
        flyway("snapshot_test", "12").migrate();
        try (var c = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()); var s = c.createStatement()) {
            s.execute("SET search_path TO snapshot_test");
            s.execute("INSERT INTO payments(payment_number,order_id,user_id,amount,currency,status,method,gateway_name) VALUES ('legacy',3,'u',12.34,'USD','PENDING','WALLET','VNPAY')");
            flyway("snapshot_test", null).migrate();
            try (var rows = s.executeQuery("SELECT amount,currency,status,provider_amount_vnd,provider_currency FROM payments WHERE payment_number='legacy'")) {
                assertTrue(rows.next()); assertEquals(new java.math.BigDecimal("12.34"), rows.getBigDecimal(1));
                assertEquals("USD", rows.getString(2)); assertEquals("PENDING", rows.getString(3));
                assertNull(rows.getObject(4)); assertNull(rows.getObject(5));
            }
        }
    }

    @Test void competingProviderReservationsAndFailedStatusRemainBlocked() throws Exception {
        flyway("race_test", null).migrate();
        var start = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> { insertThenCall(start, calls, "STRIPE"); return null; });
            var b = pool.submit(() -> { insertThenCall(start, calls, "VNPAY"); return null; });
            start.countDown(); a.get(); b.get();
            assertEquals(1, calls.get());
            try (var c = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()); var s = c.createStatement()) {
                s.execute("SET search_path TO race_test");
                for (String status : new String[]{"FAILED", "CANCELED", "SUCCEEDED"}) {
                    s.executeUpdate("UPDATE payments SET status='" + status + "' WHERE order_id=2");
                    assertEquals("23505", assertThrows(SQLException.class, () -> s.executeUpdate(
                            "INSERT INTO payments(payment_number,order_id,user_id,amount,currency,status,method) VALUES ('retry',2,'u',1,'USD','PENDING','CARD')")).getSQLState());
                }
            }
        } finally { pool.shutdownNow(); }
    }

    private void insertThenCall(CountDownLatch start, AtomicInteger calls, String provider) throws Exception {
        try (var c = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()); var s = c.createStatement()) {
            s.execute("SET search_path TO race_test");
            start.await();
            try {
                s.executeUpdate("INSERT INTO payments(payment_number,order_id,user_id,amount,currency,status,method) VALUES ('" + provider + "',2,'u',1,'USD','PENDING','CARD')");
                calls.incrementAndGet(); // Simulates provider completion followed by a lost response.
            } catch (SQLException conflict) { assertEquals("23505", conflict.getSQLState()); }
        }
    }
}
