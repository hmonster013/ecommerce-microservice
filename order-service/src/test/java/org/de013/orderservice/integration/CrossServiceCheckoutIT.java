package org.de013.orderservice.integration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.*;

class CrossServiceCheckoutIT {
    @Test
    @Timeout(value = 8, unit = TimeUnit.MINUTES)
    void threeApplicationsBootWithSeparateDatabasesAndKeycloak() throws Exception {
        String password = UUID.randomUUID().toString();
        try (PostgreSQLContainer<?> catalogDb = new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName("catalog_smoke");
             PostgreSQLContainer<?> paymentDb = new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName("payment_smoke");
             PostgreSQLContainer<?> orderDb = new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName("order_smoke");
             GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                     .withExposedPorts(6379).waitingFor(Wait.forListeningPort());
             GenericContainer<?> keycloak = new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.0.7"))
                     .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin").withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", password)
                     .withCommand("start-dev").withExposedPorts(8080)
                     .waitingFor(Wait.forHttp("/realms/master").forStatusCode(200))) {
            catalogDb.start(); paymentDb.start(); orderDb.start(); redis.start(); keycloak.start();
            String issuer = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080) + "/realms/master";
            String base = issuer.substring(0, issuer.length() - "/realms/master".length());
            String secret = UUID.randomUUID().toString();
            var kc = new Keycloak(base, password, secret);
            issuer = base + "/realms/checkout-test";
            Path temp = Path.of(System.getProperty("java.io.tmpdir"), "opencode");
            Files.createDirectories(temp);
            Path isolated = Files.createTempDirectory(temp, "catalog-launcher-");
            String resource = GenericExternalApplicationLauncher.class.getName().replace('.', '/') + ".class";
            Path copied = isolated.resolve(resource);
            Files.createDirectories(copied.getParent());
            try (var source = GenericExternalApplicationLauncher.class.getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(source);
                Files.copy(source, copied);
            }
            String tokenUri = issuer + "/protocol/openid-connect/token";
            var stripe = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var stripeCalls = new java.util.concurrent.atomic.AtomicInteger();
            var stripeFail = new java.util.concurrent.atomic.AtomicBoolean(true);
            var stripeCanceled = new java.util.concurrent.atomic.AtomicBoolean();
            var cancelKey = new java.util.concurrent.atomic.AtomicReference<String>();
            stripe.createContext("/", exchange -> {
                stripeCalls.incrementAndGet();
                exchange.getResponseHeaders().add("Request-Id", "req_cross_service_fixture");
                if (stripeFail.get()) {
                    byte[] error = "{\"error\":{\"type\":\"api_error\",\"message\":\"temporary fixture outage\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(503, error.length); exchange.getResponseBody().write(error); exchange.close(); return;
                }
                if ("POST".equals(exchange.getRequestMethod())) {
                    cancelKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                    stripeCanceled.set(true);
                }
                String payload = "{\"id\":\"pi_fixture\",\"object\":\"payment_intent\",\"livemode\":false,\"amount\":1250,\"currency\":\"usd\",\"status\":\"" + (stripeCanceled.get() ? "canceled" : "requires_payment_method") + "\",\"amount_received\":0,\"latest_charge\":null}";
                byte[] data = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json"); exchange.sendResponseHeaders(200, data.length);
                exchange.getResponseBody().write(data); exchange.close();
            });
            stripe.start();
            try (var catalog = new App(temp, isolated, "product-catalog-service", "org.de013.productcatalog.ProductCatalogApplication", catalogDb,
                         "--spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuer,
                         "--spring.data.redis.host=" + redis.getHost(), "--spring.data.redis.port=" + redis.getMappedPort(6379));
                 var payment = new App(temp, isolated, "payment-service", "org.de013.paymentservice.PaymentServiceApplication", paymentDb,
                         "--payment.internal.issuer-uri=" + issuer, "--payment.gateways.stripe.api-key=sk_test_cross_service_fixture",
                         "stripe-base=http://127.0.0.1:" + stripe.getAddress().getPort());
                 var order = new App(temp, isolated, "order-service", "org.de013.orderservice.OrderServiceApplication", orderDb,
                         "--order.checkout-expiry.enabled=false", "--order.allocation.recovery-delay-ms=3600000",
                         "--spring.cloud.openfeign.client.config.inventoryReservation.url=http://127.0.0.1:" + catalog.port,
                         "--spring.cloud.openfeign.client.config.paymentClosure.url=http://127.0.0.1:" + payment.port,
                         "--spring.security.oauth2.client.registration.catalog-inventory.client-id=order-service",
                         "--spring.security.oauth2.client.registration.catalog-inventory.client-secret=" + secret,
                         "--spring.security.oauth2.client.registration.catalog-inventory.authorization-grant-type=client_credentials",
                         "--spring.security.oauth2.client.provider.catalog-inventory.token-uri=" + tokenUri,
                         "--spring.security.oauth2.client.registration.payment-checkout-close.client-id=order-service",
                         "--spring.security.oauth2.client.registration.payment-checkout-close.client-secret=" + secret,
                         "--spring.security.oauth2.client.registration.payment-checkout-close.authorization-grant-type=client_credentials",
                         "--spring.security.oauth2.client.provider.payment-checkout-close.token-uri=" + tokenUri)) {
                catalog.check(catalogDb, "catalog_smoke");
                payment.check(paymentDb, "payment_smoke");
                order.check(orderDb, "order_smoke");
                String token = kc.token(secret);
                try (var catalogConnection = DriverManager.getConnection(catalogDb.getJdbcUrl(), catalogDb.getUsername(), catalogDb.getPassword());
                     var orderConnection = DriverManager.getConnection(orderDb.getJdbcUrl(), orderDb.getUsername(), orderDb.getPassword());
                     var paymentConnection = DriverManager.getConnection(paymentDb.getJdbcUrl(), paymentDb.getUsername(), paymentDb.getPassword())) {
                    long product = number(catalogConnection, "INSERT INTO products(name,sku,price) VALUES ('Checkout fixture','" + UUID.randomUUID() + "',12.50) RETURNING id");
                    execute(catalogConnection, "UPDATE inventory SET quantity=10 WHERE product_id=" + product);
                    String wrong = kc.wrongRoleToken(secret);
                    String catUrl = "http://127.0.0.1:" + catalog.port + "/internal/inventory-reservations";
                    String payUrl = "http://127.0.0.1:" + payment.port + "/internal/checkout-closures/999999";
                    String invalidReserve = "{\"operationId\":\"" + UUID.randomUUID() + "\",\"targetKind\":\"PRODUCT\",\"targetId\":" + product + ",\"quantity\":1,\"owner\":\"owner\"}";
                    String invalidClose = "{\"operationId\":\"" + UUID.randomUUID() + "\"}";
                    String beforeAuth = text(catalogConnection, "SELECT quantity::text || '/' || reserved_quantity::text FROM inventory WHERE product_id=" + product)
                            + "/" + number(catalogConnection, "SELECT count(*) FROM inventory_reservations")
                            + "/" + number(paymentConnection, "SELECT count(*) FROM payment_order_guard")
                            + "/" + number(orderConnection, "SELECT count(*) FROM orders");
                    assertEquals(401, request("POST", catUrl, null, invalidReserve).statusCode());
                    assertEquals(403, request("POST", catUrl, wrong, invalidReserve).statusCode());
                    assertEquals(401, request("POST", payUrl, null, invalidClose).statusCode());
                    assertEquals(403, request("POST", payUrl, wrong, invalidClose).statusCode());
                    assertEquals(beforeAuth, text(catalogConnection, "SELECT quantity::text || '/' || reserved_quantity::text FROM inventory WHERE product_id=" + product)
                            + "/" + number(catalogConnection, "SELECT count(*) FROM inventory_reservations")
                            + "/" + number(paymentConnection, "SELECT count(*) FROM payment_order_guard")
                            + "/" + number(orderConnection, "SELECT count(*) FROM orders"));
                    UUID line = UUID.randomUUID();
                    String reservation = "{\"operationId\":\"" + line + "\",\"targetKind\":\"PRODUCT\",\"targetId\":" + product + ",\"quantity\":2,\"owner\":\"owner\"}";
                    var reserved = request("POST", "http://127.0.0.1:" + catalog.port + "/internal/inventory-reservations", token, reservation);
                    assertEquals(200, reserved.statusCode(), reserved.body());
                    assertEquals("RESERVED", new ObjectMapper().readTree(reserved.body()).path("state").asText());
                    long id = number(orderConnection, "INSERT INTO orders(order_number,user_id,status,order_type,created_at,updated_at,total_amount,currency,allocation_phase,checkout_deadline,shipping_first_name,shipping_last_name,shipping_street_address,shipping_city,shipping_state,shipping_postal_code,shipping_country) VALUES ('" + UUID.randomUUID() + "','owner','PENDING','STANDARD',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,12.50,'USD','READY',CURRENT_TIMESTAMP - interval '1 minute','Test','Owner','Street','City','State','12345','US') RETURNING id");
                    execute(orderConnection, "INSERT INTO inventory_allocation_intents(operation_id,order_id,target_kind,target_id,quantity,owner_id,state) VALUES ('" + line + "'," + id + ",'PRODUCT'," + product + ",2,'owner','RESERVED')");
                    order.recover(id);
                    String operation = text(orderConnection, "SELECT checkout_close_operation::text FROM orders WHERE id=" + id);
                    assertEquals("CLOSED/CANCELLED", text(orderConnection, "SELECT allocation_phase || '/' || status FROM orders WHERE id=" + id));
                    assertEquals("RELEASED", text(orderConnection, "SELECT state FROM inventory_allocation_intents WHERE operation_id='" + line + "'"));
                    assertEquals(operation + "/SAFE/NO_PAYMENT_ATTEMPTS", text(paymentConnection, "SELECT close_operation_id::text || '/' || close_status || '/' || close_safe_proof FROM payment_order_guard WHERE order_id=" + id));
                    assertEquals("RELEASED", text(catalogConnection, "SELECT state FROM inventory_reservations WHERE operation_id='" + line + "'"));
                    assertEquals("10/0", text(catalogConnection, "SELECT quantity::text || '/' || reserved_quantity::text FROM inventory WHERE product_id=" + product));
                    for (String kind : List.of("hosted", "vnpay", "stripe")) {
                        UUID allocation = UUID.randomUUID();
                        String reserve = "{\"operationId\":\"" + allocation + "\",\"targetKind\":\"PRODUCT\",\"targetId\":" + product + ",\"quantity\":1,\"owner\":\"owner\"}";
                        assertEquals(200, request("POST", "http://127.0.0.1:" + catalog.port + "/internal/inventory-reservations", token, reserve).statusCode());
                        long oid = number(orderConnection, "INSERT INTO orders(order_number,user_id,status,order_type,created_at,updated_at,total_amount,currency,allocation_phase,checkout_deadline,shipping_first_name,shipping_last_name,shipping_street_address,shipping_city,shipping_state,shipping_postal_code,shipping_country) VALUES ('" + UUID.randomUUID() + "','owner','PENDING','STANDARD',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,12.50,'USD','READY',CURRENT_TIMESTAMP - interval '1 minute','Test','Owner','Street','City','State','12345','US') RETURNING id");
                        execute(orderConnection, "INSERT INTO inventory_allocation_intents(operation_id,order_id,target_kind,target_id,quantity,owner_id,state) VALUES ('" + allocation + "'," + oid + ",'PRODUCT'," + product + ",1,'owner','RESERVED')");
                        long pid = number(paymentConnection, "INSERT INTO payments(payment_number,order_id,user_id,amount,currency,status,method,gateway_name,stripe_payment_intent_id,stripe_intent_livemode,stripe_intent_created_by_service) VALUES ('" + UUID.randomUUID() + "'," + oid + ",'owner',12.50,'USD','PENDING','CREDIT_CARD','" + (kind.equals("vnpay") ? "VNPAY" : "STRIPE") + "'," + (kind.equals("stripe") ? "'pi_fixture',false,true" : "NULL,NULL,false") + ") RETURNING id");
                        if (kind.equals("hosted")) execute(paymentConnection, "INSERT INTO hosted_checkouts(order_id,user_id,payment_id,amount,success_url,cancel_url,reserved_at) VALUES (" + oid + ",'owner'," + pid + ",12.50,'http://localhost/success','http://localhost/cancel',CURRENT_TIMESTAMP)");
                        int before = stripeCalls.get();
                        if (kind.equals("stripe")) order.recoverError(oid); else order.recover(oid);
                        String op = text(orderConnection, "SELECT checkout_close_operation::text FROM orders WHERE id=" + oid);
                        assertEquals((kind.equals("stripe") ? "CLOSING" : "CLOSURE_HOLD") + "/PENDING", text(orderConnection, "SELECT allocation_phase || '/' || status FROM orders WHERE id=" + oid));
                        assertEquals(op + (kind.equals("stripe") ? "/CLOSING" : "/HOLD"), text(paymentConnection, "SELECT close_operation_id::text || '/' || close_status FROM payment_order_guard WHERE order_id=" + oid));
                        assertEquals("RESERVED", text(catalogConnection, "SELECT state FROM inventory_reservations WHERE operation_id='" + allocation + "'"));
                        assertEquals("RESERVED", text(orderConnection, "SELECT state FROM inventory_allocation_intents WHERE operation_id='" + allocation + "'"));
                        assertEquals(1, number(paymentConnection, "SELECT count(*) FROM payment_order_guard WHERE order_id=" + oid + " AND close_safe_proof IS NULL"));
                        assertEquals(kind.equals("hosted") ? "10/1" : kind.equals("vnpay") ? "10/2" : "10/3", text(catalogConnection, "SELECT quantity::text || '/' || reserved_quantity::text FROM inventory WHERE product_id=" + product));
                        if (!kind.equals("stripe")) { assertEquals(before, stripeCalls.get()); continue; }
                        assertTrue(stripeCalls.get() > before);
                        assertEquals(1, number(paymentConnection, "SELECT count(*) FROM payment_order_guard WHERE order_id=" + oid + " AND close_safe_proof IS NULL"));
                        stripeFail.set(false); order.recover(oid);
                        assertEquals("CLOSED/CANCELLED", text(orderConnection, "SELECT allocation_phase || '/' || status FROM orders WHERE id=" + oid));
                        assertEquals(op + "/SAFE", text(paymentConnection, "SELECT close_operation_id::text || '/' || close_status FROM payment_order_guard WHERE order_id=" + oid));
                        assertEquals("RELEASED", text(catalogConnection, "SELECT state FROM inventory_reservations WHERE operation_id='" + allocation + "'"));
                        assertEquals("RELEASED", text(orderConnection, "SELECT state FROM inventory_allocation_intents WHERE operation_id='" + allocation + "'"));
                        assertTrue(text(paymentConnection, "SELECT close_safe_proof FROM payment_order_guard WHERE order_id=" + oid).contains(op));
                        assertEquals("checkout-close-" + op + "-" + pid + "-cancel", cancelKey.get());
                        String stable = text(paymentConnection, "SELECT close_safe_proof || '/' || close_safe_at::text FROM payment_order_guard WHERE order_id=" + oid);
                        int calls = stripeCalls.get();
                        assertEquals(200, request("POST", "http://127.0.0.1:" + payment.port + "/internal/checkout-closures/" + oid, token, "{\"operationId\":\"" + op + "\"}").statusCode());
                        order.recover(oid);
                        assertEquals(stable, text(paymentConnection, "SELECT close_safe_proof || '/' || close_safe_at::text FROM payment_order_guard WHERE order_id=" + oid));
                        assertEquals(calls, stripeCalls.get());
                        assertEquals("10/2", text(catalogConnection, "SELECT quantity::text || '/' || reserved_quantity::text FROM inventory WHERE product_id=" + product));
                    }
                    String proof = text(paymentConnection, "SELECT close_safe_proof || '/' || close_safe_at::text FROM payment_order_guard WHERE order_id=" + id);
                    var replay = request("POST", "http://127.0.0.1:" + payment.port + "/internal/checkout-closures/" + id, token, "{\"operationId\":\"" + operation + "\"}");
                    assertEquals(200, replay.statusCode(), replay.body());
                    order.recover(id);
                    assertEquals(proof, text(paymentConnection, "SELECT close_safe_proof || '/' || close_safe_at::text FROM payment_order_guard WHERE order_id=" + id));
                    assertEquals("10/2", text(catalogConnection, "SELECT quantity::text || '/' || reserved_quantity::text FROM inventory WHERE product_id=" + product));
                }
            } finally { stripe.stop(0); }
        }
    }

    private static final class App implements AutoCloseable {
        private final Process process;
        private final java.util.concurrent.ExecutorService reader = Executors.newSingleThreadExecutor();
        private final Path log;
        private final int port;
        private final java.util.concurrent.BlockingQueue<String> results = new java.util.concurrent.LinkedBlockingQueue<>();

        App(Path temp, Path isolated, String module, String main, PostgreSQLContainer<?> db, String... extra) throws Exception {
            Path jar = Path.of("..", module, "target", module + "-0.0.1-SNAPSHOT.jar").toAbsolutePath();
            assertTrue(Files.isRegularFile(jar), "Missing artifact: " + jar);
            try (var sources = Files.walk(Path.of("..", module, "src", "main"))) {
                var jarTime = Files.getLastModifiedTime(jar);
                for (Path file : sources.filter(Files::isRegularFile).toList())
                    assertTrue(Files.getLastModifiedTime(file).compareTo(jarTime) <= 0, "Stale artifact " + jar + "; newer source " + file);
            }
            var jarTime = Files.getLastModifiedTime(jar);
            for (Path file : List.of(Path.of("..", "pom.xml"), Path.of("..", "common", "pom.xml"), Path.of("..", module, "pom.xml")))
                assertTrue(Files.getLastModifiedTime(file).compareTo(jarTime) <= 0, "Stale artifact " + jar + "; newer file " + file);
            try (var common = Files.walk(Path.of("..", "common", "src", "main"))) {
                for (Path file : common.filter(Files::isRegularFile).toList())
                    assertTrue(Files.getLastModifiedTime(file).compareTo(jarTime) <= 0, "Stale artifact " + jar + "; newer file " + file);
            }
            log = Files.createTempFile(temp, module + "-smoke-", ".log");
            var args = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Dloader.path=" + isolated, "-Dloader.main=" + GenericExternalApplicationLauncher.class.getName()));
            for (String value : extra) if (value.startsWith("stripe-base=")) args.add("-Dtest.stripe.base=" + value.substring(12));
            args.addAll(List.of(
                    "-cp", jar.toString(), "org.springframework.boot.loader.launch.PropertiesLauncher", main,
                    "--server.port=0", "--spring.cloud.config.enabled=false", "--spring.cloud.bus.enabled=false",
                    "--eureka.client.enabled=false", "--spring.jpa.hibernate.ddl-auto=validate", "--spring.flyway.enabled=true",
                    "--management.health.defaults.enabled=false", "--management.health.db.enabled=true",
                    "--spring.datasource.url=" + db.getJdbcUrl(), "--spring.datasource.username=" + db.getUsername(),
                    "--spring.datasource.password=" + db.getPassword(), "--logging.level.org.springframework.security=WARN"));
            for (String value : extra) if (!value.startsWith("stripe-base=")) args.add(value);
            process = new ProcessBuilder(args).redirectErrorStream(true).start();
            var ready = new CompletableFuture<Integer>();
            reader.submit(() -> {
                try (var in = new BufferedReader(new InputStreamReader(process.getInputStream())); var out = Files.newBufferedWriter(log)) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        out.write(line); out.newLine(); out.flush();
                        if (line.startsWith("TEST_READY ")) ready.complete(Integer.parseInt(line.substring(11)));
                        if (line.startsWith("TEST_RESULT ")) results.add(line);
                    }
                    ready.completeExceptionally(new IllegalStateException("Application exited before ready; log: " + log));
                } catch (Exception e) { ready.completeExceptionally(e); }
            });
            try { port = ready.get(120, TimeUnit.SECONDS); }
            catch (Exception e) { close(); throw new IllegalStateException("Startup failed; log: " + log, e); }
        }

        void recover(long id) throws Exception {
            recover(id, false);
        }
        void recoverError(long id) throws Exception { recover(id, true); }
        void recover(long id, boolean error) throws Exception {
            String correlation = UUID.randomUUID().toString();
            process.getOutputStream().write((correlation + " " + id + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            String result = results.poll(90, TimeUnit.SECONDS);
            if (error) assertEquals("TEST_RESULT " + correlation + " ERROR feign.FeignException$ServiceUnavailable", result, "Expected provider 503; log: " + log);
            else assertEquals("TEST_RESULT " + correlation + " OK", result, "Recovery failed; log: " + log);
        }

        void check(PostgreSQLContainer<?> db, String name) throws Exception {
            var response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health"))
                            .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "DB health; log: " + log);
            assertTrue(response.body().contains("UP"));
            try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT current_database(), count(*), count(*) FILTER (WHERE NOT success) FROM flyway_schema_history")) {
                assertTrue(rows.next()); assertEquals(name, rows.getString(1));
                assertTrue(rows.getInt(2) > 0); assertEquals(0, rows.getInt(3));
            }
        }

        @Override public void close() throws Exception {
            try { closeProcess(process); } finally { reader.shutdownNow(); }
        }
    }

    static void closeProcess(Process process) throws Exception {
        boolean interrupted = Thread.interrupted();
        java.io.IOException failure = null;
        try {
            try { process.getOutputStream().close(); } catch (java.io.IOException e) { failure = e; }
            try { if (!interrupted) process.waitFor(10, TimeUnit.SECONDS); }
            catch (InterruptedException e) { interrupted = true; }
        } finally {
            if (process.isAlive()) process.destroy();
            try { if (process.isAlive()) process.waitFor(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { interrupted = true; }
            finally {
                if (process.isAlive()) process.destroyForcibly();
                try { if (process.isAlive()) process.waitFor(5, TimeUnit.SECONDS); }
                catch (InterruptedException e) { interrupted = true; }
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
        if (failure != null) throw failure;
    }

    private static long number(java.sql.Connection c, String sql) throws Exception {
        try (var s = c.createStatement(); var r = s.executeQuery(sql)) { assertTrue(r.next()); return r.getLong(1); }
    }
    private static String text(java.sql.Connection c, String sql) throws Exception {
        try (var s = c.createStatement(); var r = s.executeQuery(sql)) { assertTrue(r.next()); return r.getString(1); }
    }
    private static void execute(java.sql.Connection c, String sql) throws Exception {
        try (var s = c.createStatement()) { s.executeUpdate(sql); }
    }
    private static HttpResponse<String> request(String method, String url, String token, String body) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15));
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (body != null) b.header("Content-Type", "application/json");
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(
                b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static final class Keycloak {
        final String base;
        String admin;
        final String password;
        final ObjectMapper json = new ObjectMapper();
        Keycloak(String base, String password, String secret) throws Exception {
            this.base = base;
            this.password = password;
            admin = form("/realms/master/protocol/openid-connect/token", "grant_type=password&client_id=admin-cli&username=admin&password=" + password).path("access_token").asText();
            api("POST", "/admin/realms", "{\"realm\":\"checkout-test\",\"enabled\":true}");
            String root = "/admin/realms/checkout-test";
            api("POST", root + "/clients", "{\"clientId\":\"order-service\",\"enabled\":true,\"publicClient\":false,\"serviceAccountsEnabled\":true,\"secret\":\"" + secret + "\"}");
            String order = api("GET", root + "/clients?clientId=order-service", null).get(0).path("id").asText();
            String user = api("GET", root + "/clients/" + order + "/service-account-user", null).path("id").asText();
            for (String[] pair : new String[][]{{"product-catalog-service", "catalog-inventory-reservation"}, {"payment-service", "payment-checkout-close"}}) {
                api("POST", root + "/clients", "{\"clientId\":\"" + pair[0] + "\",\"enabled\":true}");
                String client = api("GET", root + "/clients?clientId=" + pair[0], null).get(0).path("id").asText();
                api("POST", root + "/clients/" + client + "/roles", "{\"name\":\"" + pair[1] + "\"}");
                JsonNode role = api("GET", root + "/clients/" + client + "/roles/" + pair[1], null);
                api("POST", root + "/users/" + user + "/role-mappings/clients/" + client, "[" + role + "]");
                api("POST", root + "/clients/" + order + "/protocol-mappers/models", "{\"name\":\"" + pair[0] + "-audience\",\"protocol\":\"openid-connect\",\"protocolMapper\":\"oidc-audience-mapper\",\"config\":{\"included.client.audience\":\"" + pair[0] + "\",\"access.token.claim\":\"true\",\"id.token.claim\":\"false\"}}");
            }
        }
        String token(String secret) throws Exception { return form("/realms/checkout-test/protocol/openid-connect/token", "grant_type=client_credentials&client_id=order-service&client_secret=" + secret).path("access_token").asText(); }
        String wrongRoleToken(String secret) throws Exception {
            admin = form("/realms/master/protocol/openid-connect/token", "grant_type=password&client_id=admin-cli&username=admin&password=" + password).path("access_token").asText();
            String root = "/admin/realms/checkout-test";
            String order = api("GET", root + "/clients?clientId=order-service", null).get(0).path("id").asText();
            String user = api("GET", root + "/clients/" + order + "/service-account-user", null).path("id").asText();
            for (String[] pair : new String[][]{{"product-catalog-service", "catalog-inventory-reservation"}, {"payment-service", "payment-checkout-close"}}) {
                String client = api("GET", root + "/clients?clientId=" + pair[0], null).get(0).path("id").asText();
                JsonNode role = api("GET", root + "/clients/" + client + "/roles/" + pair[1], null);
                api("DELETE", root + "/users/" + user + "/role-mappings/clients/" + client, "[" + role + "]");
            }
            String result = token(secret);
            for (String[] pair : new String[][]{{"product-catalog-service", "catalog-inventory-reservation"}, {"payment-service", "payment-checkout-close"}}) {
                String client = api("GET", root + "/clients?clientId=" + pair[0], null).get(0).path("id").asText();
                JsonNode role = api("GET", root + "/clients/" + client + "/roles/" + pair[1], null);
                api("POST", root + "/users/" + user + "/role-mappings/clients/" + client, "[" + role + "]");
            }
            return result;
        }
        JsonNode form(String path, String body) throws Exception {
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "Keycloak token status"); return json.readTree(response.body());
        }
        JsonNode api(String method, String path, String body) throws Exception {
            var response = request(method, base + path, admin, body);
            assertTrue(response.statusCode() >= 200 && response.statusCode() < 300, method + " " + path + " status " + response.statusCode());
            return response.body().isBlank() ? json.nullNode() : json.readTree(response.body());
        }
    }
}
