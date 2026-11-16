package org.de013.notificationservice.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import org.junit.jupiter.api.Assumptions;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class NotificationMigrationTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeEach void resetDatabase() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()));
        jdbc.execute("DROP SCHEMA IF EXISTS public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        jdbc.execute("DROP SCHEMA IF EXISTS wrong_index_test CASCADE");
    }

    @Test void v1DataSurvivesUpgradeAndRestart() {
        var dataSource = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        Flyway.configure().dataSource(dataSource).target("1").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO notifications (user_id, channel, recipient, content) VALUES (123, 'EMAIL', 'test@example.invalid', 'history')");
        var flyway = Flyway.configure().dataSource(dataSource).load();
        flyway.migrate();
        jdbc.update("INSERT INTO notifications (user_id, channel, recipient, content) VALUES (?, 'EMAIL', 'test@example.invalid', 'uuid')", "9d382afe-7447-4cc6-ad51-6ce447bd32f0");
        flyway.migrate();
        assertEquals("123", jdbc.queryForObject("SELECT user_id FROM notifications WHERE content = 'history'", String.class));
        assertEquals("9d382afe-7447-4cc6-ad51-6ce447bd32f0", jdbc.queryForObject("SELECT user_id FROM notifications WHERE content = 'uuid'", String.class));
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname = current_schema() AND indexname IN ('idx_notification_user_id', 'idx_notification_status', 'idx_notification_channel', 'idx_notification_created_at')", Integer.class));
    }

    @Test void wrongNamedIndexStopsUpgrade() {
        var dataSource = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA wrong_index_test");
        var beforeV4 = Flyway.configure().dataSource(dataSource).schemas("wrong_index_test").target("3").load();
        beforeV4.migrate();
        jdbc.execute("CREATE INDEX idx_notification_status ON wrong_index_test.notifications (channel)");
        assertThrows(Exception.class, () -> Flyway.configure().dataSource(dataSource).schemas("wrong_index_test").load().migrate());
    }

    @Test void fingerprintDetectsLegacyDriftWithoutWritingHistory() throws Exception {
        var dataSource = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).load().migrate();
        String query = Files.readString(Path.of("schema-fingerprint.sql"));
        String reference = jdbc.queryForObject(query, String.class);
        jdbc.execute("DROP TABLE public.flyway_schema_history");
        assertEquals(reference, jdbc.queryForObject(query, String.class));
        jdbc.execute("ALTER TABLE notifications ALTER COLUMN recipient SET DEFAULT 'different'");
        assertNotEquals(reference, jdbc.queryForObject(query, String.class));
        jdbc.execute("ALTER TABLE notifications ALTER COLUMN recipient DROP DEFAULT");
        jdbc.execute("DROP INDEX idx_notification_status");
        assertNotEquals(reference, jdbc.queryForObject(query, String.class));
        jdbc.execute("CREATE INDEX idx_notification_status ON notifications (status)");
        jdbc.execute("ALTER SEQUENCE notifications_id_seq OWNED BY NONE");
        assertNotEquals(reference, jdbc.queryForObject(query, String.class));
        jdbc.execute("ALTER SEQUENCE notifications_id_seq OWNED BY notifications.id");
        jdbc.execute("ALTER TABLE payment_email_request DROP CONSTRAINT payment_email_request_notification_id_fkey");
        assertNotEquals(reference, jdbc.queryForObject(query, String.class));
    }

    @Test void fingerprintRejectsRoutinesAndUserTriggers() throws Exception {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()));
        Flyway.configure().dataSource(new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())).load().migrate();
        String query = Files.readString(Path.of("schema-fingerprint.sql"));
        jdbc.execute("CREATE FUNCTION public.extra() RETURNS integer LANGUAGE sql AS 'SELECT 1'");
        assertThrows(Exception.class, () -> jdbc.queryForObject(query, String.class));
        jdbc.execute("DROP FUNCTION public.extra()");
        jdbc.execute("CREATE PROCEDURE public.extra() LANGUAGE sql AS 'SELECT 1'");
        assertThrows(Exception.class, () -> jdbc.queryForObject(query, String.class));
        jdbc.execute("DROP PROCEDURE public.extra()");
        jdbc.execute("CREATE SCHEMA trigger_helpers");
        try {
            jdbc.execute("CREATE FUNCTION trigger_helpers.noop() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RETURN NEW; END'");
            jdbc.execute("CREATE TRIGGER extra BEFORE INSERT ON public.notifications FOR EACH ROW EXECUTE FUNCTION trigger_helpers.noop()");
            assertThrows(Exception.class, () -> jdbc.queryForObject(query, String.class));
        } finally {
            jdbc.execute("DROP SCHEMA trigger_helpers CASCADE");
        }
    }

    @Test void twoDatabasesMatchAtEveryValidatedVersion() throws Exception {
        var actual = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        var admin = new JdbcTemplate(actual);
        admin.execute("CREATE DATABASE notification_reference");
        try {
            var reference = new DriverManagerDataSource(DB.getJdbcUrl().replaceFirst("/[^/?]+(\\?.*)?$", "/notification_reference$1"), DB.getUsername(), DB.getPassword());
            String query = Files.readString(Path.of("schema-fingerprint.sql"));
            var actualJdbc = new JdbcTemplate(actual);
            var referenceJdbc = new JdbcTemplate(reference);
            for (int version = 1; version <= 4; version++) {
                var actualFlyway = Flyway.configure().dataSource(actual).target(Integer.toString(version)).load();
                var referenceFlyway = Flyway.configure().dataSource(reference).target(Integer.toString(version)).load();
                actualFlyway.migrate();
                referenceFlyway.migrate();
                referenceFlyway.validate();
                assertEquals(referenceJdbc.queryForObject(query, String.class), actualJdbc.queryForObject(query, String.class));
            }
            referenceJdbc.update("UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '1'");
            assertThrows(Exception.class, () -> Flyway.configure().dataSource(reference).load().validate());
            actualJdbc.execute("ALTER TABLE notifications ALTER COLUMN recipient SET DEFAULT 'drift'");
            assertNotEquals(referenceJdbc.queryForObject(query, String.class), actualJdbc.queryForObject(query, String.class));
        } finally {
            admin.execute("DROP DATABASE notification_reference WITH (FORCE)");
        }
    }

    @Test void operatorPreflightUsesDatabaseIdentityAndValidatedReference() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        var actual = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        var admin = new JdbcTemplate(actual);
        admin.execute("CREATE DATABASE notification_reference");
        Path temp = Files.createTempDirectory("notification-preflight-");
        try {
            var reference = new DriverManagerDataSource(DB.getJdbcUrl().replaceFirst("/[^/?]+(\\?.*)?$", "/notification_reference$1"), DB.getUsername(), DB.getPassword());
            var referenceJdbc = new JdbcTemplate(reference);
            String config = "[actual]\nhost=localhost\ndbname=" + DB.getDatabaseName() + "\nuser=" + DB.getUsername() + "\npassword=" + DB.getPassword() + "\n"
                    + "[same]\nhost=localhost\ndbname=" + DB.getDatabaseName() + "\nuser=" + DB.getUsername() + "\npassword=" + DB.getPassword() + "\n"
                    + "[reference]\nhost=localhost\ndbname=notification_reference\nuser=" + DB.getUsername() + "\npassword=" + DB.getPassword() + "\n"
                    + "[restricted]\nhost=localhost\ndbname=" + DB.getDatabaseName() + "\nuser=preflight_restricted\npassword=fixture_only\n";
            Path services = temp.resolve("pg_service.conf");
            Files.writeString(services, config);
            DB.copyFileToContainer(MountableFile.forHostPath(services), "/tmp/preflight-services.conf");
            DB.copyFileToContainer(MountableFile.forHostPath(Path.of("schema-fingerprint.sql").toAbsolutePath()), "/tmp/preflight-fingerprint.sql");
            Files.writeString(temp.resolve("psql.cmd"), "@echo off\r\nset service=%2\r\nshift\r\nif \"%9\"==\"-f\" (\r\n docker exec -e PGSERVICEFILE=/tmp/preflight-services.conf " + DB.getContainerId() + " psql service=%service% -X -q -t -A -v ON_ERROR_STOP=1 -f /tmp/preflight-fingerprint.sql\r\n) else (\r\n docker exec -e PGSERVICEFILE=/tmp/preflight-services.conf " + DB.getContainerId() + " psql %*\r\n)\r\nexit /b %ERRORLEVEL%\r\n");
            admin.execute("CREATE ROLE preflight_restricted LOGIN PASSWORD 'fixture_only'");
            admin.execute("REVOKE EXECUTE ON FUNCTION pg_catalog.pg_control_system() FROM PUBLIC");
            for (int version = 1; version <= 4; version++) {
                Flyway.configure().dataSource(actual).target(Integer.toString(version)).load().migrate();
                var flyway = Flyway.configure().dataSource(reference).target(Integer.toString(version)).load();
                flyway.migrate();
                flyway.validate();
                assertEquals(0, preflight(temp, version, "actual", "reference"));
                assertNotEquals(0, preflight(temp, version, "actual", "same", "Actual and reference must be distinct databases"));
            }
            assertNotEquals(0, preflight(temp, 3, "actual", "reference"));
            referenceJdbc.update("UPDATE flyway_schema_history SET type = 'BASELINE' WHERE version = '1'");
            assertNotEquals(0, preflight(temp, 4, "actual", "reference"));
            referenceJdbc.update("UPDATE flyway_schema_history SET type = 'SQL' WHERE version = '1'");
            referenceJdbc.update("UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '1'");
            assertThrows(Exception.class, () -> Flyway.configure().dataSource(reference).load().validate());
            referenceJdbc.update("UPDATE flyway_schema_history SET checksum = checksum - 1 WHERE version = '1'");
            Flyway.configure().dataSource(reference).load().validate();
            admin.execute("ALTER TABLE notifications ALTER COLUMN recipient SET DEFAULT 'drift'");
            assertNotEquals(0, preflight(temp, 4, "actual", "reference"));
            admin.execute("ALTER TABLE notifications ALTER COLUMN recipient DROP DEFAULT");
            admin.execute("CREATE FUNCTION public.extra() RETURNS integer LANGUAGE sql AS 'SELECT 1'");
            assertNotEquals(0, preflight(temp, 4, "actual", "reference"));
            admin.execute("DROP FUNCTION public.extra()");
            admin.execute("CREATE SCHEMA trigger_helpers");
            admin.execute("CREATE FUNCTION trigger_helpers.noop() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RETURN NEW; END'");
            admin.execute("CREATE TRIGGER extra BEFORE INSERT ON public.notifications FOR EACH ROW EXECUTE FUNCTION trigger_helpers.noop()");
            assertNotEquals(0, preflight(temp, 4, "actual", "reference"));
            admin.execute("DROP SCHEMA trigger_helpers CASCADE");
            assertNotEquals(0, preflight(temp, 4, "restricted", "reference", "permission denied for function pg_control_system"));
            admin.execute("GRANT EXECUTE ON FUNCTION pg_catalog.pg_control_system() TO preflight_restricted");
            admin.execute("GRANT USAGE ON SCHEMA public TO preflight_restricted");
            admin.execute("GRANT SELECT ON ALL TABLES IN SCHEMA public TO preflight_restricted");
            admin.execute("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO preflight_restricted");
            var restrictedJdbc = new JdbcTemplate(new DriverManagerDataSource(DB.getJdbcUrl(), "preflight_restricted", "fixture_only"));
            assertEquals(admin.queryForObject(Files.readString(Path.of("schema-fingerprint.sql")), String.class), restrictedJdbc.queryForObject(Files.readString(Path.of("schema-fingerprint.sql")), String.class));
            assertEquals(0, preflight(temp, 4, "restricted", "reference"));
            admin.execute("DROP SCHEMA public CASCADE");
            admin.execute("CREATE SCHEMA public");
            referenceJdbc.execute("DROP SCHEMA public CASCADE");
            referenceJdbc.execute("CREATE SCHEMA public");
            Flyway.configure().dataSource(actual).target("1").load().migrate();
            admin.update("INSERT INTO notifications (user_id, channel, recipient, content) VALUES (123, 'EMAIL', 'test@example.invalid', 'legacy')");
            admin.execute("DROP TABLE public.flyway_schema_history");
            var referenceV1 = Flyway.configure().dataSource(reference).target("1").load();
            referenceV1.migrate();
            referenceV1.validate();
            assertEquals(0, preflight(temp, 1, "actual", "reference"));
            var adoption = Flyway.configure().dataSource(actual).baselineVersion("1").baselineOnMigrate(false).load();
            adoption.baseline();
            adoption.migrate();
            adoption.validate();
            adoption.migrate();
            assertEquals("123", admin.queryForObject("SELECT user_id FROM notifications WHERE content = 'legacy'", String.class));
            admin.update("INSERT INTO notifications (user_id, channel, recipient, content) VALUES (?, 'EMAIL', 'test@example.invalid', 'uuid')", "9d382afe-7447-4cc6-ad51-6ce447bd32f0");
            assertEquals(1, admin.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND type = 'BASELINE'", Integer.class));
            assertEquals(3, admin.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version IN ('2','3','4') AND type = 'SQL' AND success", Integer.class));
            assertEquals(0, admin.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND type = 'SQL'", Integer.class));
        } finally {
            admin.execute("DROP DATABASE notification_reference WITH (FORCE)");
            if (Boolean.TRUE.equals(admin.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'preflight_restricted')", Boolean.class))) {
                admin.execute("REVOKE EXECUTE ON FUNCTION pg_catalog.pg_control_system() FROM preflight_restricted");
                admin.execute("DROP OWNED BY preflight_restricted");
                admin.execute("DROP ROLE preflight_restricted");
            }
            try (var paths = Files.walk(temp)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); } });
            }
        }
    }

    private int preflight(Path shim, int version, String actual, String reference) throws Exception {
        return preflight(shim, version, actual, reference, null);
    }

    private int preflight(Path shim, int version, String actual, String reference, String expectedOutput) throws Exception {
        var process = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "RemoteSigned", "-File", Path.of("schema-preflight.ps1").toAbsolutePath().toString(), "-ExpectedVersion", Integer.toString(version), "-ActualService", actual, "-ReferenceService", reference)
                .redirectErrorStream(true);
        process.environment().put("PATH", shim.toAbsolutePath() + ";" + System.getenv("PATH"));
        var running = process.start();
        String output = new String(running.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        int exit = running.waitFor();
        assertTrue(!output.contains("cannot be loaded because running scripts is disabled"), output);
        if (expectedOutput != null) assertTrue(output.contains(expectedOutput), output);
        if (exit != 0 && expectedOutput == null) System.err.println(output);
        return exit;
    }
}
