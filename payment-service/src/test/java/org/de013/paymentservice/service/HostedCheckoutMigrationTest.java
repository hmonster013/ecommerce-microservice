package org.de013.paymentservice.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class HostedCheckoutMigrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test void persistsSessionGenerationAndProtectsOneHostedPaymentPerOrder() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).target("9").load().migrate();
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO payments(id,payment_number,order_id,user_id,amount,currency,status,method) VALUES(1,'HOSTED-1',1,'owner',25,'USD','PENDING','CARD')");
            statement.executeUpdate("INSERT INTO hosted_checkouts(order_id,user_id,payment_id,amount,success_url,cancel_url,reserved_at) VALUES(1,'owner',1,25,'https://fixture.test/checkout/result','https://fixture.test/checkout/result',CURRENT_TIMESTAMP)");
            assertEquals("23505", assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO hosted_checkouts(order_id,user_id,payment_id,amount,success_url,cancel_url,reserved_at) VALUES(1,'other',1,25,'x','x',CURRENT_TIMESTAMP)")).getSQLState());
            statement.executeUpdate("UPDATE hosted_checkouts SET session_id='cs_fixture',session_state='OPEN',generation=1 WHERE order_id=1");
            try (var result = statement.executeQuery("SELECT user_id,session_id,generation FROM hosted_checkouts WHERE order_id=1")) {
                assertTrue(result.next()); assertEquals("owner", result.getString(1)); assertEquals("cs_fixture", result.getString(2)); assertEquals(1, result.getInt(3));
            }
        }
    }
}
