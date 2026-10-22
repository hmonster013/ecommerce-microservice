package org.de013.userservice.integration;

import jakarta.validation.Validation;
import org.de013.userservice.dto.KeycloakProfileProvisionRequest;
import org.de013.userservice.dto.SyncUserRequest;
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
class IdentityColumnMigrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void upgradesExistingProfilesAndAcceptsLongEmailUsernamesWithoutLosingUniqueness() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("2").load().migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO users (keycloak_id, username, email, created_at, updated_at) "
                        + "VALUES ('existing-subject', 'existing', 'existing@example.test', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            }
            String failedIdentity = "a".repeat(38) + "@example.test";
            assertEquals(51, failedIdentity.length());
            SQLException before = assertThrows(SQLException.class, () -> insert(connection, failedIdentity));
            assertEquals("22001", before.getSQLState());

            Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .load().migrate();
            for (int length : new int[]{51, 101, 255}) {
                String identity = "a".repeat(length - 13) + "@example.test";
                insert(connection, identity);
                try (var query = connection.prepareStatement("SELECT username, email FROM users WHERE username = ?")) {
                    query.setString(1, identity);
                    try (var result = query.executeQuery()) {
                        assertTrue(result.next());
                        assertEquals(identity, result.getString("email"));
                        assertEquals(identity, result.getString("username"));
                        assertFalse(result.next());
                    }
                }
                SQLException duplicateUsername = assertThrows(SQLException.class,
                        () -> insert(connection, identity, "other@example.test"));
                assertEquals("23505", duplicateUsername.getSQLState());
                SQLException duplicateEmail = assertThrows(SQLException.class,
                        () -> insert(connection, "other-" + length, identity));
                assertEquals("23505", duplicateEmail.getSQLState());
            }
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT email, version FROM users WHERE keycloak_id = 'existing-subject'")) {
                assertTrue(result.next());
                assertEquals("existing@example.test", result.getString("email"));
                assertEquals(0L, result.getLong("version"));
            }
            SQLException tooLong = assertThrows(SQLException.class,
                    () -> insert(connection, "a".repeat(243) + "@example.test"));
            assertEquals("22001", tooLong.getSQLState());
        }
    }

    @Test
    void rejectsIdentityFieldsBeyondThePersistenceContractAtTheDtoBoundary() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            String tooLong = "a".repeat(256);
            var provision = new KeycloakProfileProvisionRequest("subject", tooLong, tooLong, "Test", "User");
            var sync = new SyncUserRequest("subject", tooLong, tooLong, "Test", "User");
            for (Object request : new Object[]{provision, sync}) {
                var violations = validator.validate(request);
                assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("username")
                        && v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName().equals("Size")));
                assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")
                        && v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName().equals("Size")));
            }
        }
    }

    private void insert(java.sql.Connection connection, String identity) throws SQLException {
        insert(connection, identity, identity);
    }

    private void insert(java.sql.Connection connection, String username, String email) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO users "
                + "(keycloak_id, username, email, created_at, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, username);
            statement.setString(3, email);
            statement.executeUpdate();
        }
    }
}
