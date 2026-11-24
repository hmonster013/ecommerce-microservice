package org.de013.orderservice.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class CheckoutMigrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test void upgradesBaselineAndEnforcesDurableUniqueAttemptBeforeSideEffects() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).target("2").load().migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO orders(order_number,user_id,status,order_type,created_at,updated_at) VALUES ('OLD','owner','PENDING','STANDARD',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            }
            Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();
            String key = UUID.randomUUID().toString();
            try (var insert = connection.prepareStatement("INSERT INTO checkout_attempts(attempt_key,user_id) VALUES (?,?)")) {
                insert.setString(1, key); insert.setString(2, "owner"); insert.executeUpdate();
                insert.setString(2, "other");
                assertEquals("23505", assertThrows(SQLException.class, insert::executeUpdate).getSQLState());
            }
            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT user_id FROM checkout_attempts")) {
                assertTrue(result.next()); assertEquals("owner", result.getString(1)); assertFalse(result.next());
            }
            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT order_number,checkout_key FROM orders")) {
                assertTrue(result.next()); assertEquals("OLD", result.getString(1)); assertNull(result.getString(2));
            }
        }
    }
}
