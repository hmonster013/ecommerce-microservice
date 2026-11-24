package org.de013.shoppingcart;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class CartNotifConfigServerArtifactTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @TempDir Path temp;

    @Test
    void packagedServicesUseMandatoryConfigServerAndPreserveMigrationHistory() throws Exception {
        Path config = temp.resolve("config");
        Files.createDirectories(config);
        for (String app : List.of("shopping-cart-service", "notification-service")) {
            Files.copy(Path.of("..", "config-server", "src", "main", "resources", "config", app + ".yml"),
                    config.resolve(app + ".yml"), StandardCopyOption.REPLACE_EXISTING);
        }
        Path isolated = temp.resolve("launcher");
        Path classFile = isolated.resolve("org/de013/shoppingcart/ArtifactConfigLauncher.class");
        Files.createDirectories(classFile.getParent());
        try (var input = ArtifactConfigLauncher.class.getResourceAsStream("ArtifactConfigLauncher.class")) {
            assertNotNull(input);
            Files.copy(input, classFile);
        }
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        String endpoint = "http://127.0.0.1:" + port;
        try (Child server = new Child("config-server", null, List.of(
                "--server.port=" + port, "--spring.profiles.active=native",
                "--spring.cloud.config.server.native.search-locations=" + config.toUri(),
                "--spring.cloud.bus.enabled=false", "--eureka.client.enabled=false"))) {
            waitForConfig(endpoint, server);
            for (String module : List.of("shopping-cart-service", "notification-service")) {
                String body = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(endpoint + "/" + module + "/default"))
                        .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString()).body();
                assertTrue(body.contains(module + ".yml"));
                assertTrue(body.contains("spring.flyway.enabled"));
                assertTrue(body.contains("spring.flyway.baseline-on-migrate"));
                assertTrue(body.contains("spring.jpa.hibernate.ddl-auto"));
                String db = module.startsWith("shopping") ? "artifact_cart" : "artifact_notification";
                try (var c = connection("postgres"); var s = c.createStatement()) { s.execute("CREATE DATABASE " + db); }
                List<String> args = serviceArgs(endpoint, module);
                String history;
                try (Child app = new Child(module, isolated, args, db)) {
                    app.awaitReady();
                    history = history(db);
                    assertEquals(module.startsWith("shopping") ? 3 : 4, history.split(";", -1).length - 1);
                    assertFalse(history.contains("BASELINE"));
                    try (var c = connection(db); var s = c.createStatement()) { s.execute("CREATE TABLE artifact_sentinel (value integer); INSERT INTO artifact_sentinel VALUES (42)"); }
                }
                try (Child app = new Child(module, isolated, args, db)) {
                    app.awaitReady();
                    assertEquals(history, history(db));
                    assertEquals(42, scalar(db, "SELECT value FROM artifact_sentinel"));
                }
                String dirty = db + "_dirty";
                try (var c = connection("postgres"); var s = c.createStatement()) { s.execute("CREATE DATABASE " + dirty); }
                try (var c = connection(dirty); var s = c.createStatement()) { s.execute("CREATE TABLE sentinel(value integer); INSERT INTO sentinel VALUES (42)"); }
                try (Child app = new Child(module, isolated, args, dirty)) { app.awaitFailure(); }
                assertEquals(42, scalar(dirty, "SELECT value FROM sentinel"));
                assertEquals(0, scalar(dirty, "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='flyway_schema_history'"));
            }
        }
        try (Child app = new Child("shopping-cart-service", isolated, serviceArgs(endpoint, "shopping-cart-service"), "artifact_cart")) {
            app.awaitFailure();
        }
    }

    private List<String> serviceArgs(String endpoint, String module) {
        var args = new ArrayList<>(List.of("--spring.config.import=configserver:" + endpoint,
                "--server.port=0", "--spring.cloud.bus.enabled=false", "--eureka.client.enabled=false",
                "--management.health.defaults.enabled=false"));
        if (module.equals("notification-service")) args.add("--notification.payment-email.events.enabled=false");
        return args;
    }

    private java.sql.Connection connection(String database) throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl().replaceFirst("/[^/?]+(\\?.*)?$", "/" + database), postgres.getUsername(), postgres.getPassword());
    }

    private int scalar(String database, String sql) throws Exception {
        try (var c = connection(database); var s = c.createStatement(); var r = s.executeQuery(sql)) { assertTrue(r.next()); return r.getInt(1); }
    }

    private String history(String database) throws Exception {
        try (var c = connection(database); var s = c.createStatement(); var r = s.executeQuery(
                "SELECT coalesce(string_agg(version || ':' || type || ':' || checksum || ':' || success, ';' ORDER BY installed_rank) || ';', '') FROM flyway_schema_history WHERE version IS NOT NULL")) {
            assertTrue(r.next()); return r.getString(1);
        }
    }

    private void waitForConfig(String endpoint, Child server) throws Exception {
        var client = HttpClient.newHttpClient();
        for (int i = 0; i < 120; i++) {
            if (!server.process.isAlive()) fail("Config server exited: " + server.log);
            try {
                if (client.send(HttpRequest.newBuilder(URI.create(endpoint + "/shopping-cart-service/default")).timeout(Duration.ofSeconds(2)).build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200) return;
            } catch (java.io.IOException ignored) { }
            Thread.sleep(1000);
        }
        fail("Config server did not start: " + server.log);
    }

    private final class Child implements AutoCloseable {
        final Process process;
        final Path log;
        final CompletableFuture<Integer> ready = new CompletableFuture<>();
        final Thread reader;

        Child(String module, Path isolated, List<String> extra) throws Exception { this(module, isolated, extra, null); }
        Child(String module, Path isolated, List<String> extra, String database) throws Exception {
            Path jar = Path.of("..", module, "target", module + "-0.0.1-SNAPSHOT.jar").toAbsolutePath();
            assertTrue(Files.isRegularFile(jar), "Missing artifact " + jar);
            var jarTime = Files.getLastModifiedTime(jar);
            for (Path source : List.of(Path.of("..", module, "src", "main"), Path.of("..", "common", "src", "main"))) {
                try (var files = Files.walk(source)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) assertTrue(Files.getLastModifiedTime(file).compareTo(jarTime) <= 0, "Stale artifact " + jar + ": " + file);
                }
            }
            for (Path pom : List.of(Path.of("..", "pom.xml"), Path.of("..", "common", "pom.xml"), Path.of("..", module, "pom.xml")))
                assertTrue(Files.getLastModifiedTime(pom).compareTo(jarTime) <= 0, "Stale artifact " + jar + ": " + pom);
            var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString()));
            if (isolated == null) command.addAll(List.of("-jar", jar.toString()));
            else {
                String main = module.startsWith("shopping") ? "org.de013.shoppingcart.ShoppingCartApplication" : "org.de013.notificationservice.NotificationServiceApplication";
                command.addAll(List.of("-Dloader.path=" + isolated, "-Dloader.main=" + ArtifactConfigLauncher.class.getName(), "-cp", jar.toString(), "org.springframework.boot.loader.launch.PropertiesLauncher", main));
            }
            command.addAll(extra);
            var builder = new ProcessBuilder(command).redirectErrorStream(true);
            if (database != null) {
                String url = postgres.getJdbcUrl().replaceFirst("/[^/?]+(\\?.*)?$", "/" + database);
                builder.environment().put(module.startsWith("shopping") ? "SHOPPING_CART_DATASOURCE_URL" : "NOTIFICATION_SERVICE_DATASOURCE_URL", url);
                builder.environment().put("SPRING_DATASOURCE_USERNAME", postgres.getUsername());
                builder.environment().put("SPRING_DATASOURCE_PASSWORD", postgres.getPassword());
            }
            log = Files.createTempFile(temp, module, ".log");
            process = builder.start();
            reader = new Thread(() -> {
                try (var input = new BufferedReader(new InputStreamReader(process.getInputStream())); var output = Files.newBufferedWriter(log)) {
                    String line;
                    while ((line = input.readLine()) != null) {
                        output.write(line); output.newLine(); output.flush();
                        if (line.startsWith("TEST_READY ")) ready.complete(Integer.parseInt(line.substring(11)));
                    }
                    ready.completeExceptionally(new IllegalStateException("Exited before ready: " + log));
                } catch (Exception e) { ready.completeExceptionally(e); }
            });
            reader.start();
        }

        void awaitReady() throws Exception { ready.get(180, TimeUnit.SECONDS); }
        void awaitFailure() throws Exception {
            assertTrue(process.waitFor(180, TimeUnit.SECONDS), "Expected startup failure: " + log);
            assertNotEquals(0, process.exitValue(), "Expected startup failure: " + log);
            assertFalse(ready.isDone() && !ready.isCompletedExceptionally(), "Unexpected ready: " + log);
        }
        @Override public void close() throws Exception {
            boolean interrupted = Thread.interrupted();
            try {
                process.getOutputStream().close();
                if (!interrupted && !process.waitFor(10, TimeUnit.SECONDS)) process.destroy();
                if (process.isAlive() && !process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
                reader.join(5000);
            } catch (InterruptedException e) { interrupted = true; process.destroyForcibly(); }
            finally { if (process.isAlive()) process.destroyForcibly(); if (interrupted) Thread.currentThread().interrupt(); }
        }
    }
}
