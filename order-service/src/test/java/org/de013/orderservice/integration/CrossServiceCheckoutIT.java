package org.de013.orderservice.integration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
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
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.*;

class CrossServiceCheckoutIT {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CrossServiceCheckoutIT.class);
    @Test
    @Timeout(value = 12, unit = TimeUnit.MINUTES)
    void threeApplicationsBootWithSeparateDatabasesAndKeycloak() throws Exception {
        String password = UUID.randomUUID().toString();
        try (PostgreSQLContainer<?> catalogDb = new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName("catalog_smoke");
             PostgreSQLContainer<?> paymentDb = new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName("payment_smoke");
             PostgreSQLContainer<?> orderDb = new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName("order_smoke");
             GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                     .withExposedPorts(6379).waitingFor(Wait.forListeningPort());
             KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
             GenericContainer<?> keycloak = new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.0.7"))
                     .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin").withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", password)
                     .withCommand("start-dev").withExposedPorts(8080)
                     .waitingFor(Wait.forHttp("/realms/master").forStatusCode(200))) {
            catalogDb.start(); paymentDb.start(); orderDb.start(); redis.start(); kafka.start(); keycloak.start();
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
            var stripeRequests = new java.util.concurrent.CopyOnWriteArrayList<String>();
            var stripeFail = new java.util.concurrent.atomic.AtomicBoolean(true);
            var stripeCanceled = new java.util.concurrent.atomic.AtomicBoolean();
            var cancelKey = new java.util.concurrent.atomic.AtomicReference<String>();
            var retryKey = new java.util.concurrent.atomic.AtomicReference<String>();
            var retryConfirmPosts = new java.util.concurrent.atomic.AtomicInteger();
            var retryStatus = new java.util.concurrent.atomic.AtomicReference<>("requires_payment_method");
            stripe.createContext("/", exchange -> {
                stripeCalls.incrementAndGet();
                stripeRequests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
                exchange.getResponseHeaders().add("Request-Id", "req_cross_service_fixture");
                if ("GET".equals(exchange.getRequestMethod()) && "/v1/account".equals(exchange.getRequestURI().getPath())) {
                    byte[] account = "{\"id\":\"acct_fixture\",\"object\":\"account\",\"charges_enabled\":true,\"payouts_enabled\":true,\"details_submitted\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, account.length); exchange.getResponseBody().write(account); exchange.close(); return;
                }
                if (stripeFail.get()) {
                    byte[] error = "{\"error\":{\"type\":\"api_error\",\"message\":\"temporary fixture outage\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(503, error.length); exchange.getResponseBody().write(error); exchange.close(); return;
                }
                String path = exchange.getRequestURI().getPath();
                boolean retry = path.contains("pi_retry_fixture");
                if (retry && "POST".equals(exchange.getRequestMethod()) && path.endsWith("/confirm")) {
                    retryConfirmPosts.incrementAndGet();
                    retryKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                    retryStatus.set("processing");
                } else if (!retry && "POST".equals(exchange.getRequestMethod()) && path.endsWith("/cancel")) {
                    cancelKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                    stripeCanceled.set(true);
                } else if (!"GET".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(404, -1); exchange.close(); return;
                }
                String payload = "{\"id\":\"" + (retry ? "pi_retry_fixture" : "pi_fixture") + "\",\"object\":\"payment_intent\",\"livemode\":false,\"amount\":1250,\"currency\":\"usd\",\"status\":\"" + (retry ? retryStatus.get() : stripeCanceled.get() ? "canceled" : "requires_payment_method") + "\",\"amount_received\":0,\"latest_charge\":null}";
                byte[] data = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json"); exchange.sendResponseHeaders(200, data.length);
                exchange.getResponseBody().write(data); exchange.close();
            });
            stripe.start();
            int orderPort;
            try (var socket = new ServerSocket(0)) { orderPort = socket.getLocalPort(); }
            try (var catalog = new App(temp, isolated, "product-catalog-service", "org.de013.productcatalog.ProductCatalogApplication", catalogDb,
                         "--spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuer,
                         "--spring.data.redis.host=" + redis.getHost(), "--spring.data.redis.port=" + redis.getMappedPort(6379));
                 var payment = new App(temp, isolated, "payment-service", "org.de013.paymentservice.PaymentServiceApplication", paymentDb,
                          "--payment.internal.issuer-uri=" + issuer, "--payment.gateways.stripe.api-key=sk_test_cross_service_fixture",
                          "--payment.gateways.stripe.webhook-secret=whsec_cross_service_fixture",
                          "--spring.cloud.openfeign.client.config.order-service.url=http://127.0.0.1:" + orderPort,
                          "--payment.events.enabled=true", "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                          "stripe-base=http://127.0.0.1:" + stripe.getAddress().getPort());
                 var catalogProxy = new CatalogProxy(catalog.port);
                 var order = new App(temp, isolated, "order-service", "org.de013.orderservice.OrderServiceApplication", orderDb,
                           "--server.port=" + orderPort, "--order.payment-events.enabled=true", "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                           "--order.checkout-expiry.enabled=false", "--order.allocation.recovery-delay-ms=1000",
                          "--order.reconciliation.enabled=true", "--order.reconciliation.delay-ms=3600000",
                          "--spring.cloud.openfeign.client.config.inventoryReservation.url=http://127.0.0.1:" + catalogProxy.port(),
                          "--spring.cloud.openfeign.client.config.paymentClosure.url=http://127.0.0.1:" + payment.port,
                          "--spring.cloud.openfeign.client.config.paymentReconciliation.url=http://127.0.0.1:" + payment.port,
                         "--spring.security.oauth2.client.registration.catalog-inventory.client-id=order-service",
                         "--spring.security.oauth2.client.registration.catalog-inventory.client-secret=" + secret,
                         "--spring.security.oauth2.client.registration.catalog-inventory.authorization-grant-type=client_credentials",
                         "--spring.security.oauth2.client.provider.catalog-inventory.token-uri=" + tokenUri,
                         "--spring.security.oauth2.client.registration.payment-checkout-close.client-id=order-service",
                         "--spring.security.oauth2.client.registration.payment-checkout-close.client-secret=" + secret,
                         "--spring.security.oauth2.client.registration.payment-checkout-close.authorization-grant-type=client_credentials",
                          "--spring.security.oauth2.client.provider.payment-checkout-close.token-uri=" + tokenUri,
                          "--spring.security.oauth2.client.registration.payment-reconciliation.client-id=order-service",
                          "--spring.security.oauth2.client.registration.payment-reconciliation.client-secret=" + secret,
                          "--spring.security.oauth2.client.registration.payment-reconciliation.authorization-grant-type=client_credentials",
                          "--spring.security.oauth2.client.provider.payment-reconciliation.token-uri=" + tokenUri)) {
                catalog.check(catalogDb, "catalog_smoke");
                payment.check(paymentDb, "payment_smoke");
                assertTrue(stripeRequests.contains("GET /v1/account"), "Stripe gateway health check was not called");
                order.check(orderDb, "order_smoke");
                assertEquals(orderPort, order.port, "Payment Feign must target the selected Order port");
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
                    String readOnly = kc.paymentRoleToken(secret, "payment-checkout-close");
                    String closeOnly = kc.paymentRoleToken(secret, "payment-reconciliation-read");
                    JsonNode readClaims = new ObjectMapper().readTree(java.util.Base64.getUrlDecoder().decode(readOnly.split("\\.")[1]));
                    JsonNode closeClaims = new ObjectMapper().readTree(java.util.Base64.getUrlDecoder().decode(closeOnly.split("\\.")[1]));
                    assertEquals("order-service", readClaims.path("azp").asText());
                    assertTrue(readClaims.path("resource_access").path("payment-service").path("roles").toString().contains("payment-reconciliation-read"));
                    assertFalse(readClaims.path("resource_access").path("payment-service").path("roles").toString().contains("payment-checkout-close"));
                    assertTrue(closeClaims.path("resource_access").path("payment-service").path("roles").toString().contains("payment-checkout-close"));
                    assertFalse(closeClaims.path("resource_access").path("payment-service").path("roles").toString().contains("payment-reconciliation-read"));
                    String readUrl = "http://127.0.0.1:" + payment.port + "/internal/reconciliation/orders/" + id;
                    var read = request("GET", readUrl, readOnly, null);
                    assertEquals(200, read.statusCode());
                    assertNotNull(java.time.Instant.parse(new ObjectMapper().readTree(read.body()).path("observedAt").asText()));
                    assertEquals(403, request("POST", payUrl, readOnly, invalidClose).statusCode());
                    assertEquals(403, request("GET", readUrl, closeOnly, null).statusCode());
                    String wrongAudience = kc.wrongPaymentAudienceToken(secret);
                    JsonNode wrongClaims = new ObjectMapper().readTree(java.util.Base64.getUrlDecoder().decode(wrongAudience.split("\\.")[1]));
                    assertEquals("order-service", wrongClaims.path("azp").asText());
                    assertTrue(wrongClaims.path("resource_access").path("payment-service").path("roles").toString().contains("payment-reconciliation-read"));
                    assertFalse(wrongClaims.path("aud").toString().contains("payment-service"));
                    assertEquals(401, request("GET", readUrl, wrongAudience, null).statusCode());
                    var fakeHeaders = HttpRequest.newBuilder(URI.create(readUrl)).header("X-User-Id", "order-service").header("X-User-Roles", "payment-reconciliation-read").GET().build();
                    assertEquals(401, HttpClient.newHttpClient().send(fakeHeaders, HttpResponse.BodyHandlers.ofString()).statusCode());
                    execute(orderConnection, "UPDATE orders SET allocation_phase='CLOSURE_HOLD' WHERE id=" + id);
                    String beforeReconciliation = text(orderConnection, "SELECT row_to_json(o)::text FROM orders o WHERE id=" + id)
                            + text(orderConnection, "SELECT row_to_json(i)::text FROM inventory_allocation_intents i WHERE order_id=" + id)
                            + text(paymentConnection, "SELECT row_to_json(g)::text FROM payment_order_guard g WHERE order_id=" + id)
                            + text(catalogConnection, "SELECT row_to_json(r)::text FROM inventory_reservations r WHERE operation_id='" + line + "'");
                    order.reconcile();
                    var evidence = new ObjectMapper().readTree(text(orderConnection, "SELECT evidence::text FROM reconciliation_revision WHERE order_id=" + id + " ORDER BY revision DESC LIMIT 1"));
                    assertEquals(id, evidence.path("payment").path("orderId").asLong());
                    assertTrue(evidence.path("payment").path("guardPresent").asBoolean());
                    assertFalse(evidence.path("payment").path("observedAt").isMissingNode());
                    assertTrue(text(orderConnection, "SELECT reasons::text FROM reconciliation_revision WHERE order_id=" + id + " ORDER BY revision DESC LIMIT 1").contains("HOLD_WITH_INVENTORY"));
                    assertEquals(beforeReconciliation, text(orderConnection, "SELECT row_to_json(o)::text FROM orders o WHERE id=" + id)
                            + text(orderConnection, "SELECT row_to_json(i)::text FROM inventory_allocation_intents i WHERE order_id=" + id)
                            + text(paymentConnection, "SELECT row_to_json(g)::text FROM payment_order_guard g WHERE order_id=" + id)
                            + text(catalogConnection, "SELECT row_to_json(r)::text FROM inventory_reservations r WHERE operation_id='" + line + "'"));
                    execute(orderConnection, "UPDATE orders SET allocation_phase='CLOSED' WHERE id=" + id);
                    // Directly seed historical financial truth after closure; this does not exercise a callback.
                    long success = number(paymentConnection, "INSERT INTO payments(payment_number,order_id,user_id,amount,currency,status,method,gateway_name) VALUES ('" + UUID.randomUUID() + "'," + id + ",'owner',12.50,'USD','SUCCEEDED','CARD','STRIPE') RETURNING id");
                    execute(paymentConnection, "INSERT INTO payment_transactions(payment_id,type,amount,status,currency) VALUES (" + success + ",'CHARGE',12.50,'SUCCEEDED','USD')");
                    execute(paymentConnection, "UPDATE payment_order_guard SET reconciliation_hold=true WHERE order_id=" + id);
                    String financialBefore = text(orderConnection, "SELECT row_to_json(o)::text FROM orders o WHERE id=" + id)
                            + text(orderConnection, "SELECT row_to_json(i)::text FROM inventory_allocation_intents i WHERE order_id=" + id)
                            + text(paymentConnection, "SELECT row_to_json(p)::text FROM payments p WHERE id=" + success)
                            + text(paymentConnection, "SELECT row_to_json(g)::text FROM payment_order_guard g WHERE order_id=" + id)
                            + text(catalogConnection, "SELECT row_to_json(r)::text FROM inventory_reservations r WHERE operation_id='" + line + "'");
                    execute(orderConnection, "UPDATE reconciliation_finding SET next_check_at=clock_timestamp()-interval '1 second' WHERE order_id=" + id);
                    order.reconcile();
                    JsonNode financialEvidence = new ObjectMapper().readTree(text(orderConnection, "SELECT evidence::text FROM reconciliation_revision WHERE order_id=" + id + " ORDER BY revision DESC LIMIT 1"));
                    JsonNode attempt = financialEvidence.path("payment").path("attempts").get(0);
                    assertEquals(success, attempt.path("paymentId").asLong());
                    assertTrue(attempt.path("successfulChargeOrCapture").asBoolean());
                    assertEquals(0, new java.math.BigDecimal("12.50").compareTo(attempt.path("amount").decimalValue()));
                    String financialReasons = text(orderConnection, "SELECT reasons::text FROM reconciliation_revision WHERE order_id=" + id + " ORDER BY revision DESC LIMIT 1");
                    assertTrue(financialReasons.contains(org.de013.orderservice.entity.enums.ReconciliationReason.SUCCESS_WITH_CLOSED_OR_RELEASED.name()));
                    assertTrue(financialReasons.contains(org.de013.orderservice.entity.enums.ReconciliationReason.PAYMENT_ORDER_MISMATCH.name()));
                    assertEquals(financialBefore, text(orderConnection, "SELECT row_to_json(o)::text FROM orders o WHERE id=" + id)
                            + text(orderConnection, "SELECT row_to_json(i)::text FROM inventory_allocation_intents i WHERE order_id=" + id)
                            + text(paymentConnection, "SELECT row_to_json(p)::text FROM payments p WHERE id=" + success)
                            + text(paymentConnection, "SELECT row_to_json(g)::text FROM payment_order_guard g WHERE order_id=" + id)
                            + text(catalogConnection, "SELECT row_to_json(r)::text FROM inventory_reservations r WHERE operation_id='" + line + "'"));
                    for (String kind : List.of("hosted", "vnpay", "stripe")) {
                        UUID allocation = UUID.randomUUID();
                        String reserve = "{\"operationId\":\"" + allocation + "\",\"targetKind\":\"PRODUCT\",\"targetId\":" + product + ",\"quantity\":1,\"owner\":\"owner\"}";
                        assertEquals(200, request("POST", "http://127.0.0.1:" + catalog.port + "/internal/inventory-reservations", token, reserve).statusCode());
                        long oid = number(orderConnection, "INSERT INTO orders(order_number,user_id,status,order_type,created_at,updated_at,total_amount,currency,allocation_phase,checkout_deadline,shipping_first_name,shipping_last_name,shipping_street_address,shipping_city,shipping_state,shipping_postal_code,shipping_country) VALUES ('" + UUID.randomUUID() + "','owner','PENDING','STANDARD',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,12.50,'USD','READY',CURRENT_TIMESTAMP - interval '1 minute','Test','Owner','Street','City','State','12345','US') RETURNING id");
                        execute(orderConnection, "INSERT INTO inventory_allocation_intents(operation_id,order_id,target_kind,target_id,quantity,owner_id,state) VALUES ('" + allocation + "'," + oid + ",'PRODUCT'," + product + ",1,'owner','RESERVED')");
                        long pid = number(paymentConnection, "INSERT INTO payments(payment_number,order_id,user_id,amount,currency,status,method,gateway_name,stripe_payment_intent_id,stripe_intent_livemode,stripe_intent_created_by_service) VALUES ('" + UUID.randomUUID() + "'," + oid + ",'owner',12.50,'USD','PENDING','CARD','" + (kind.equals("vnpay") ? "VNPAY" : "STRIPE") + "'," + (kind.equals("stripe") ? "'pi_fixture',false,true" : "NULL,NULL,false") + ") RETURNING id");
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
                    long retryOrder = 0, retryPayment = 0;
                    UUID retryLine = null, retryLine2 = null;
                    for (boolean future : List.of(true, false)) {
                        UUID allocation = UUID.randomUUID(), foreign = UUID.randomUUID(), op = UUID.randomUUID();
                        String reserve = "{\"operationId\":\"" + allocation + "\",\"targetKind\":\"PRODUCT\",\"targetId\":" + product + ",\"quantity\":1,\"owner\":\"owner\"}";
                        assertEquals(200, request("POST", catUrl, token, reserve).statusCode());
                        long claimOrder = number(orderConnection, "INSERT INTO orders(order_number,user_id,status,order_type,created_at,updated_at,total_amount,currency,allocation_phase,checkout_deadline,shipping_first_name,shipping_last_name,shipping_street_address,shipping_city,shipping_state,shipping_postal_code,shipping_country) VALUES ('" + UUID.randomUUID() + "','owner','PENDING','STANDARD',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,12.50,'USD','READY',CURRENT_TIMESTAMP + interval '" + (future ? "1 hour" : "-1 minute") + "','Test','Owner','Street','City','State','12345','US') RETURNING id");
                        execute(orderConnection, "INSERT INTO inventory_allocation_intents(operation_id,order_id,target_kind,target_id,quantity,owner_id,state) VALUES ('" + allocation + "'," + claimOrder + ",'PRODUCT'," + product + ",1,'owner','RESERVED')");
                        if (future) {
                            execute(orderConnection, "UPDATE orders SET allocation_phase='ALLOCATING',checkout_deadline=NULL WHERE id=" + claimOrder);
                            await(() -> number(orderConnection, "SELECT count(*) FROM orders WHERE id=" + claimOrder + " AND allocation_phase='READY' AND checkout_deadline BETWEEN CURRENT_TIMESTAMP + interval '29 minutes' AND CURRENT_TIMESTAMP + interval '31 minutes'") == 1);
                        }
                        String deadline = text(orderConnection, "SELECT checkout_deadline::text FROM orders WHERE id=" + claimOrder);
                        if (future) {
                            retryOrder = claimOrder; retryLine = allocation;
                            // Payment and PI provenance are seeded; the failure is a signed HTTP webhook, not payment creation.
                            retryPayment = number(paymentConnection, "INSERT INTO payments(payment_number,order_id,user_id,amount,currency,status,method,gateway_name,stripe_payment_intent_id,stripe_intent_livemode,stripe_intent_created_by_service) VALUES ('" + UUID.randomUUID() + "'," + claimOrder + ",'owner',12.50,'USD','PENDING','CARD','STRIPE','pi_retry_fixture',false,true) RETURNING id");
                            String failureEvent = "evt_failed_" + UUID.randomUUID().toString().replace("-", "");
                            var failure = stripeWebhook(payment.port, failureEvent, "payment_intent.payment_failed", "pi_retry_fixture", "requires_payment_method");
                            assertEquals(200, failure.statusCode(), failure.body());
                            assertEquals(1, number(paymentConnection, "SELECT count(*) FROM processed_stripe_events WHERE event_id='" + failureEvent + "'"));
                            assertEquals(1, number(paymentConnection, "SELECT count(*) FROM payment_outbox WHERE payment_id=" + retryPayment + " AND event_type='PaymentFailed'"));
                            assertEquals("FAILED", text(paymentConnection, "SELECT status FROM payments WHERE id=" + retryPayment));
                            assertEquals("READY/PENDING", text(orderConnection, "SELECT allocation_phase || '/' || status FROM orders WHERE id=" + claimOrder));
                            assertEquals(deadline, text(orderConnection, "SELECT checkout_deadline::text FROM orders WHERE id=" + claimOrder));
                            assertEquals("RESERVED", text(orderConnection, "SELECT state FROM inventory_allocation_intents WHERE operation_id='" + allocation + "'"));
                            assertEquals("RESERVED", text(catalogConnection, "SELECT state FROM inventory_reservations WHERE operation_id='" + allocation + "'"));
                            String paymentNumber = text(paymentConnection, "SELECT payment_number FROM payments WHERE id=" + retryPayment);
                            assertEquals(1, number(paymentConnection, "SELECT count(*) FROM payments WHERE id=" + retryPayment + " AND blocking=true AND verified_release=false"));
                            String clientKey = "client-" + UUID.randomUUID();
                            var orderResponse = ownerRequest("GET", "http://127.0.0.1:" + order.port + "/orders/" + claimOrder, null);
                            assertEquals(200, orderResponse.statusCode(), orderResponse.body());
                            assertTrue(new ObjectMapper().readTree(orderResponse.body()).path("paymentRetryEligible").asBoolean());
                            var confirm = ownerRequest("POST", "http://127.0.0.1:" + payment.port + "/payments/" + retryPayment + "/confirm?paymentMethodId=pm_fixture", clientKey);
                            assertEquals(200, confirm.statusCode(), confirm.body() + "; Stripe requests: " + stripeRequests);
                            assertEquals(text(paymentConnection, "SELECT provider_key::text FROM payment_retry_attempt WHERE payment_id=" + retryPayment), retryKey.get());
                            assertNotEquals(clientKey, retryKey.get());
                            assertEquals("PROCESSING/pi_retry_fixture", text(paymentConnection, "SELECT status || '/' || stripe_payment_intent_id FROM payments WHERE id=" + retryPayment));
                            assertEquals(paymentNumber, text(paymentConnection, "SELECT payment_number FROM payments WHERE id=" + retryPayment));
                            assertEquals(1, retryConfirmPosts.get());
                            assertEquals(409, ownerRequest("POST", "http://127.0.0.1:" + payment.port + "/payments/" + retryPayment + "/confirm?paymentMethodId=pm_fixture", clientKey).statusCode());
                            assertEquals(1, retryConfirmPosts.get());
                            assertEquals(text(paymentConnection, "SELECT provider_key::text FROM payment_retry_attempt WHERE payment_id=" + retryPayment), retryKey.get());
                            var cancel = ownerRequest("DELETE", "http://127.0.0.1:" + order.port + "/orders/" + claimOrder, null);
                            assertEquals(409, cancel.statusCode(), cancel.body());
                            assertEquals("READY/PENDING", text(orderConnection, "SELECT allocation_phase || '/' || status FROM orders WHERE id=" + claimOrder));
                            assertEquals(deadline, text(orderConnection, "SELECT checkout_deadline::text FROM orders WHERE id=" + claimOrder));
                            assertEquals("RESERVED", text(orderConnection, "SELECT state FROM inventory_allocation_intents WHERE operation_id='" + allocation + "'"));
                            assertEquals("RESERVED", text(catalogConnection, "SELECT state FROM inventory_reservations WHERE operation_id='" + allocation + "'"));
                            assertEquals(1, number(paymentConnection, "SELECT count(*) FROM payments WHERE order_id=" + claimOrder));
                        }
                        if (!future) execute(orderConnection, "UPDATE orders SET allocation_phase='CLOSING',checkout_close_operation='" + op + "',checkout_close_claim='" + foreign + "',checkout_close_claim_until=CURRENT_TIMESTAMP + interval '1 hour' WHERE id=" + claimOrder);
                        int calls = stripeCalls.get();
                        order.recover(claimOrder);
                        assertEquals((future ? "READY" : "CLOSING") + "/PENDING", text(orderConnection, "SELECT allocation_phase || '/' || status FROM orders WHERE id=" + claimOrder));
                        assertEquals("RESERVED", text(orderConnection, "SELECT state FROM inventory_allocation_intents WHERE operation_id='" + allocation + "'"));
                        assertEquals("RESERVED", text(catalogConnection, "SELECT state FROM inventory_reservations WHERE operation_id='" + allocation + "'"));
                        assertEquals(future ? "10/3" : "10/4", text(catalogConnection, "SELECT quantity::text || '/' || reserved_quantity::text FROM inventory WHERE product_id=" + product));
                        if (!future) assertEquals(0, number(paymentConnection, "SELECT count(*) FROM payment_order_guard WHERE order_id=" + claimOrder));
                        assertEquals(calls, stripeCalls.get());
                        if (!future) {
                            assertEquals(foreign.toString(), text(orderConnection, "SELECT checkout_close_claim::text FROM orders WHERE id=" + claimOrder));
                            execute(orderConnection, "UPDATE orders SET checkout_close_claim_until=CURRENT_TIMESTAMP - interval '1 second' WHERE id=" + claimOrder);
                            order.recover(claimOrder);
                            assertEquals("CLOSED/CANCELLED", text(orderConnection, "SELECT allocation_phase || '/' || status FROM orders WHERE id=" + claimOrder));
                            assertEquals(op.toString(), text(orderConnection, "SELECT checkout_close_operation::text FROM orders WHERE id=" + claimOrder));
                            assertEquals(op + "/SAFE/NO_PAYMENT_ATTEMPTS", text(paymentConnection, "SELECT close_operation_id::text || '/' || close_status || '/' || close_safe_proof FROM payment_order_guard WHERE order_id=" + claimOrder));
                            assertEquals("RELEASED", text(orderConnection, "SELECT state FROM inventory_allocation_intents WHERE operation_id='" + allocation + "'"));
                            assertEquals("RELEASED", text(catalogConnection, "SELECT state FROM inventory_reservations WHERE operation_id='" + allocation + "'"));
                            assertEquals("10/3", text(catalogConnection, "SELECT quantity::text || '/' || reserved_quantity::text FROM inventory WHERE product_id=" + product));
                            String stableProof = text(paymentConnection, "SELECT close_safe_proof || '/' || close_safe_at::text FROM payment_order_guard WHERE order_id=" + claimOrder);
                            order.recover(claimOrder);
                            assertEquals(stableProof, text(paymentConnection, "SELECT close_safe_proof || '/' || close_safe_at::text FROM payment_order_guard WHERE order_id=" + claimOrder));
                            assertEquals("10/3", text(catalogConnection, "SELECT quantity::text || '/' || reserved_quantity::text FROM inventory WHERE product_id=" + product));
                            assertEquals(calls, stripeCalls.get());
                        }
                    }
                    // A signed canceled attempt is not checkout cancellation; it has its own order and PI.
                    long cancelProduct = number(catalogConnection, "INSERT INTO products(name,sku,price) VALUES ('Cancel fixture','" + UUID.randomUUID() + "',12.50) RETURNING id");
                    execute(catalogConnection, "UPDATE inventory SET quantity=3 WHERE product_id=" + cancelProduct);
                    UUID cancelLine = UUID.randomUUID();
                    assertEquals(200, request("POST", catUrl, token, "{\"operationId\":\"" + cancelLine + "\",\"targetKind\":\"PRODUCT\",\"targetId\":" + cancelProduct + ",\"quantity\":1,\"owner\":\"owner\"}").statusCode());
                    long cancelOrder = number(orderConnection, "INSERT INTO orders(order_number,user_id,status,order_type,created_at,updated_at,total_amount,currency,allocation_phase,checkout_deadline,shipping_first_name,shipping_last_name,shipping_street_address,shipping_city,shipping_state,shipping_postal_code,shipping_country) VALUES ('" + UUID.randomUUID() + "','owner','PENDING','STANDARD',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,12.50,'USD','READY',CURRENT_TIMESTAMP + interval '30 minutes','Test','Owner','Street','City','State','12345','US') RETURNING id");
                    execute(orderConnection, "INSERT INTO inventory_allocation_intents(operation_id,order_id,target_kind,target_id,quantity,owner_id,state) VALUES ('" + cancelLine + "'," + cancelOrder + ",'PRODUCT'," + cancelProduct + ",1,'owner','RESERVED')");
                    String cancelDeadline = text(orderConnection, "SELECT checkout_deadline::text FROM orders WHERE id=" + cancelOrder);
                    long cancelPayment = number(paymentConnection, "INSERT INTO payments(payment_number,order_id,user_id,amount,currency,status,method,gateway_name,stripe_payment_intent_id,stripe_intent_livemode,stripe_intent_created_by_service) VALUES ('" + UUID.randomUUID() + "'," + cancelOrder + ",'owner',12.50,'USD','PENDING','CARD','STRIPE','pi_cancel_fixture',false,true) RETURNING id");
                    String cancelEvent = "evt_cancel_" + UUID.randomUUID().toString().replace("-", "");
                    var canceled = stripeWebhook(payment.port, cancelEvent, "payment_intent.canceled", "pi_cancel_fixture", "canceled");
                    assertEquals(200, canceled.statusCode(), canceled.body());
                    assertEquals(1, number(paymentConnection, "SELECT count(*) FROM payments WHERE id=" + cancelPayment + " AND status='CANCELED' AND blocking=false AND verified_release=true"));
                    assertEquals(1, number(paymentConnection, "SELECT count(*) FROM processed_stripe_events WHERE event_id='" + cancelEvent + "'"));
                    order.recover(cancelOrder);
                    assertEquals("READY/PENDING", text(orderConnection, "SELECT allocation_phase || '/' || status FROM orders WHERE id=" + cancelOrder));
                    assertEquals(cancelDeadline, text(orderConnection, "SELECT checkout_deadline::text FROM orders WHERE id=" + cancelOrder));
                    assertEquals("RESERVED", text(orderConnection, "SELECT state FROM inventory_allocation_intents WHERE operation_id='" + cancelLine + "'"));
                    assertEquals("RESERVED", text(catalogConnection, "SELECT state FROM inventory_reservations WHERE operation_id='" + cancelLine + "'"));
                    // Contradict Payment's SAFE guard after the first real release commits; this tests recheck, not a callback.
                    UUID releaseFirst = UUID.fromString("00000000-0000-0000-0000-000000000011");
                    UUID releaseSecond = UUID.fromString("00000000-0000-0000-0000-000000000012");
                    long holdOrder = number(orderConnection, "INSERT INTO orders(order_number,user_id,status,order_type,created_at,updated_at,total_amount,currency,allocation_phase,checkout_deadline,shipping_first_name,shipping_last_name,shipping_street_address,shipping_city,shipping_state,shipping_postal_code,shipping_country) VALUES ('" + UUID.randomUUID() + "','owner','PENDING','STANDARD',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,12.50,'USD','READY',CURRENT_TIMESTAMP - interval '1 minute','Test','Owner','Street','City','State','12345','US') RETURNING id");
                    for (UUID operationId : List.of(releaseFirst, releaseSecond)) {
                        assertEquals(200, request("POST", catUrl, token, "{\"operationId\":\"" + operationId + "\",\"targetKind\":\"PRODUCT\",\"targetId\":" + product + ",\"quantity\":1,\"owner\":\"owner\"}").statusCode());
                        execute(orderConnection, "INSERT INTO inventory_allocation_intents(operation_id,order_id,target_kind,target_id,quantity,owner_id,state) VALUES ('" + operationId + "'," + holdOrder + ",'PRODUCT'," + product + ",1,'owner','RESERVED')");
                    }
                    var releaseBarrier = catalogProxy.pause(releaseFirst, "release");
                    String[] holdOperation = new String[1];
                    var recovery = CompletableFuture.runAsync(() -> {
                        try { order.recover(holdOrder); } catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
                    });
                    try {
                        assertTrue(releaseBarrier.arrived.await(20, TimeUnit.SECONDS), "First release did not commit");
                        assertEquals(200, releaseBarrier.status);
                        holdOperation[0] = text(orderConnection, "SELECT checkout_close_operation::text FROM orders WHERE id=" + holdOrder);
                        assertEquals(holdOperation[0] + "/SAFE", text(paymentConnection, "SELECT close_operation_id::text || '/' || close_status FROM payment_order_guard WHERE order_id=" + holdOrder));
                        assertEquals("RELEASED", text(catalogConnection, "SELECT state FROM inventory_reservations WHERE operation_id='" + releaseFirst + "'"));
                        // Simulate the guard effects of late success; this is not callback or financial-truth evidence.
                        execute(paymentConnection, "UPDATE payment_order_guard SET reconciliation_hold=true,close_status='HOLD',close_reason='LATE_SUCCESS' WHERE order_id=" + holdOrder + " AND close_operation_id='" + holdOperation[0] + "' AND close_status='SAFE'");
                    } finally { releaseBarrier.resume.countDown(); }
                    recovery.get(30, TimeUnit.SECONDS);
                    assertEquals("CLOSURE_HOLD/PENDING/true", text(orderConnection, "SELECT allocation_phase || '/' || status || '/' || (cancelled_at IS NULL)::text FROM orders WHERE id=" + holdOrder));
                    assertEquals(holdOperation[0], text(orderConnection, "SELECT checkout_close_operation::text FROM orders WHERE id=" + holdOrder));
                    assertEquals(holdOperation[0] + "/HOLD", text(paymentConnection, "SELECT close_operation_id::text || '/' || close_status FROM payment_order_guard WHERE order_id=" + holdOrder));
                    for (String table : List.of("inventory_allocation_intents", "inventory_reservations")) {
                        var connection = table.equals("inventory_reservations") ? catalogConnection : orderConnection;
                        assertEquals("RELEASED", text(connection, "SELECT state FROM " + table + " WHERE operation_id='" + releaseFirst + "'"));
                        assertEquals("RESERVED", text(connection, "SELECT state FROM " + table + " WHERE operation_id='" + releaseSecond + "'"));
                    }
                    order.recover(holdOrder);
                    assertEquals("CLOSURE_HOLD/PENDING", text(orderConnection, "SELECT allocation_phase || '/' || status FROM orders WHERE id=" + holdOrder));
                    assertEquals("RESERVED", text(catalogConnection, "SELECT state FROM inventory_reservations WHERE operation_id='" + releaseSecond + "'"));
                    assertEquals("RESERVED", text(orderConnection, "SELECT state FROM inventory_allocation_intents WHERE operation_id='" + releaseSecond + "'"));
                    // This second line is a multi-item fixture, not normal order creation.
                    long product2 = number(catalogConnection, "INSERT INTO products(name,sku,price) VALUES ('Second item','" + UUID.randomUUID() + "',1.00) RETURNING id");
                    execute(catalogConnection, "UPDATE inventory SET quantity=10 WHERE product_id=" + product2);
                    retryLine2 = UUID.randomUUID();
                    assertEquals(200, request("POST", catUrl, token, "{\"operationId\":\"" + retryLine2 + "\",\"targetKind\":\"PRODUCT\",\"targetId\":" + product2 + ",\"quantity\":1,\"owner\":\"owner\"}").statusCode());
                    execute(orderConnection, "INSERT INTO inventory_allocation_intents(operation_id,order_id,target_kind,target_id,quantity,owner_id,state) VALUES ('" + retryLine2 + "'," + retryOrder + ",'PRODUCT'," + product2 + ",1,'owner','RESERVED')");
                    UUID fulfillFirst = retryLine.toString().compareTo(retryLine2.toString()) < 0 ? retryLine : retryLine2;
                    var fulfillBarrier = catalogProxy.pause(fulfillFirst, "fulfill");
                    String stableDeadline = text(orderConnection, "SELECT checkout_deadline::text FROM orders WHERE id=" + retryOrder);
                    final long paidOrder = retryOrder, paidPayment = retryPayment;
                    final UUID firstLine = retryLine, secondLine = retryLine2;
                    order.kill();
                    assertFalse(order.process.isAlive());
                    retryStatus.set("succeeded");
                    var sync = ownerRequest("POST", "http://127.0.0.1:" + payment.port + "/payments/" + retryPayment + "/sync", null);
                    assertEquals(200, sync.statusCode(), sync.body());
                    assertEquals("SUCCEEDED", text(paymentConnection, "SELECT status FROM payments WHERE id=" + paidPayment));
                    await(() -> number(paymentConnection, "SELECT count(*) FROM payment_outbox WHERE payment_id=" + paidPayment + " AND event_type='PaymentSucceeded' AND state='PUBLISHED' AND published_at IS NOT NULL") == 1);
                    String successEvent = text(paymentConnection, "SELECT event_id::text FROM payment_outbox WHERE payment_id=" + paidPayment + " AND event_type='PaymentSucceeded'");
                    assertEquals(1, number(orderConnection, "SELECT count(*) FROM orders WHERE id=" + paidOrder + " AND allocation_phase='READY' AND status='PENDING' AND accepted_payment_id IS NULL"));
                    assertEquals(0, number(orderConnection, "SELECT count(*) FROM payment_event_inbox WHERE event_id='" + successEvent + "' AND result='ACCEPTED'"));
                    assertEquals(2, number(catalogConnection, "SELECT count(*) FROM inventory_reservations WHERE operation_id IN ('" + firstLine + "','" + secondLine + "') AND state='RESERVED'"));
                    assertEquals(1, retryConfirmPosts.get());
                    try (var catchup = order.restart()) {
                    assertEquals(orderPort, catchup.port);
                    assertTrue(fulfillBarrier.arrived.await(60, TimeUnit.SECONDS), "First fulfillment did not commit");
                    assertEquals(200, fulfillBarrier.status);
                    assertEquals("FULFILL_PENDING/PAID", text(orderConnection, "SELECT allocation_phase || '/' || status FROM orders WHERE id=" + paidOrder));
                    assertEquals(1, number(orderConnection, "SELECT count(*) FROM payment_event_inbox WHERE event_id='" + successEvent + "' AND result='ACCEPTED'"));
                    assertEquals(paidPayment, number(orderConnection, "SELECT accepted_payment_id FROM orders WHERE id=" + paidOrder));
                    assertEquals("FULFILLED", text(catalogConnection, "SELECT state FROM inventory_reservations WHERE operation_id='" + fulfillFirst + "'"));
                    assertEquals(2, number(orderConnection, "SELECT count(*) FROM inventory_allocation_intents WHERE order_id=" + paidOrder + " AND state='RESERVED'"));
                    catchup.kill();
                    assertFalse(catchup.process.isAlive());
                    fulfillBarrier.resume.countDown();
                    try (var restarted = catchup.restart()) {
                    assertEquals(orderPort, restarted.port);
                    await(() -> number(paymentConnection, "SELECT count(*) FROM payment_outbox WHERE payment_id=" + paidPayment + " AND event_type='PaymentSucceeded' AND state='PUBLISHED' AND published_at IS NOT NULL") == 1
                            && number(orderConnection, "SELECT count(*) FROM payment_event_inbox WHERE event_id='" + successEvent + "' AND result='ACCEPTED'") == 1
                            && number(orderConnection, "SELECT count(*) FROM orders WHERE id=" + paidOrder + " AND status='PAID' AND allocation_phase='FULFILLED' AND accepted_payment_id=" + paidPayment) == 1
                            && number(orderConnection, "SELECT count(*) FROM inventory_allocation_intents WHERE order_id=" + paidOrder + " AND state='FULFILLED'") == 2
                            && number(catalogConnection, "SELECT count(*) FROM inventory_reservations WHERE operation_id IN ('" + firstLine + "','" + secondLine + "') AND state='FULFILLED'") == 2);
                    assertEquals(stableDeadline, text(orderConnection, "SELECT checkout_deadline::text FROM orders WHERE id=" + paidOrder));
                    assertEquals(successEvent, text(paymentConnection, "SELECT event_id::text FROM payment_outbox WHERE payment_id=" + paidPayment + " AND event_type='PaymentSucceeded'"));
                    assertEquals(1, retryConfirmPosts.get());
                    assertEquals("9/3", text(catalogConnection, "SELECT quantity::text || '/' || reserved_quantity::text FROM inventory WHERE product_id=" + product));
                    assertEquals("9/0", text(catalogConnection, "SELECT quantity::text || '/' || reserved_quantity::text FROM inventory WHERE product_id=" + product2));
                    }
                    }
                }
            } finally { stripe.stop(0); }
        }
    }

    static final class App implements AutoCloseable {
        private final Process process;
        private final Path temp, isolated;
        private final String module, main;
        private final PostgreSQLContainer<?> db;
        private final String[] extra;
        private final java.util.concurrent.ExecutorService reader = Executors.newSingleThreadExecutor();
        private final Path log;
        final int port;
        private final java.util.concurrent.BlockingQueue<String> results = new java.util.concurrent.LinkedBlockingQueue<>();

        App(Path temp, Path isolated, String module, String main, PostgreSQLContainer<?> db, String... extra) throws Exception {
            this.temp = temp; this.isolated = isolated; this.module = module; this.main = main; this.db = db; this.extra = extra.clone();
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
            String dbUser = java.util.Arrays.stream(extra).filter(v -> v.startsWith("test-db-user=")).map(v -> v.substring("test-db-user=".length())).findFirst().orElse(db.getUsername());
            String dbPassword = java.util.Arrays.stream(extra).filter(v -> v.startsWith("test-db-password=")).map(v -> v.substring("test-db-password=".length())).findFirst().orElse(db.getPassword());
            boolean runtimeDb = java.util.Arrays.stream(extra).anyMatch(v -> v.startsWith("test-db-user="));
            args.addAll(List.of(
                    "-cp", jar.toString(), "org.springframework.boot.loader.launch.PropertiesLauncher", main,
                    "--spring.cloud.config.enabled=false", "--spring.cloud.bus.enabled=false",
                    "--eureka.client.enabled=false", "--spring.jpa.hibernate.ddl-auto=validate", "--spring.flyway.enabled=" + !runtimeDb,
                    "--management.health.defaults.enabled=false", "--management.health.db.enabled=true",
                    "--spring.datasource.url=" + db.getJdbcUrl(), "--spring.datasource.username=" + dbUser,
                    "--spring.datasource.password=" + dbPassword, "--logging.level.org.springframework.security=WARN"));
            if (java.util.Arrays.stream(extra).noneMatch(value -> value.startsWith("--server.port="))) args.add("--server.port=0");
            for (String value : extra) if (!value.startsWith("stripe-base=") && !value.startsWith("test-db-user=") && !value.startsWith("test-db-password=")) args.add(value);
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

        App restart() throws Exception { return new App(temp, isolated, module, main, db, extra); }
        App restartAs(String user, String password) throws Exception {
            var options = new ArrayList<String>();
            for (String option : extra) if (!option.startsWith("test-db-user=") && !option.startsWith("test-db-password=")) options.add(option);
            options.add("test-db-user=" + user); options.add("test-db-password=" + password);
            return new App(temp, isolated, module, main, db, options.toArray(String[]::new));
        }
        void kill() throws Exception {
            process.destroyForcibly();
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Order JVM did not terminate");
        }

        void recover(long id) throws Exception {
            recover(id, false);
        }
        void recoverError(long id) throws Exception { recover(id, true); }
        void reconcile() throws Exception {
            String correlation = UUID.randomUUID().toString();
            process.getOutputStream().write((correlation + " RECONCILE\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            assertEquals("TEST_RESULT " + correlation + " OK", results.poll(90, TimeUnit.SECONDS), "Reconciliation failed; log: " + log);
        }
        void compensate() throws Exception {
            String correlation = UUID.randomUUID().toString();
            process.getOutputStream().write((correlation + " COMPENSATE\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            assertEquals("TEST_RESULT " + correlation + " OK", results.poll(90, TimeUnit.SECONDS), "Compensation failed; log: " + log);
        }
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
            try { if (process.isAlive()) closeProcess(process); } finally { reader.shutdownNow(); }
        }
    }

    private static final class CatalogProxy implements AutoCloseable {
        private final com.sun.net.httpserver.HttpServer server;
        private final java.util.concurrent.ExecutorService executor = Executors.newCachedThreadPool();
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        private final int targetPort;
        private volatile Barrier barrier;

        CatalogProxy(int targetPort) throws Exception {
            this.targetPort = targetPort;
            server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                try {
                    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.targetPort + exchange.getRequestURI()))
                            .timeout(Duration.ofSeconds(20));
                    exchange.getRequestHeaders().forEach((name, values) -> {
                        if (!List.of("host", "content-length", "connection", "upgrade", "expect", "http2-settings").contains(name.toLowerCase(java.util.Locale.ROOT)))
                            values.forEach(value -> request.header(name, value));
                    });
                    byte[] body = exchange.getRequestBody().readAllBytes();
                    var response = client.send(request.method(exchange.getRequestMethod(), HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
                    Barrier current = barrier;
                    if (current != null && exchange.getRequestURI().getPath().endsWith("/" + current.operation + "/" + current.action)
                            && current.triggered.compareAndSet(false, true)) {
                        current.status = response.statusCode();
                        current.arrived.countDown();
                        if (!current.resume.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("Catalog barrier timed out");
                    }
                    response.headers().map().forEach((name, values) -> {
                        if (!List.of("content-length", "transfer-encoding", "connection").contains(name.toLowerCase(java.util.Locale.ROOT)))
                            exchange.getResponseHeaders().put(name, values);
                    });
                    exchange.sendResponseHeaders(response.statusCode(), response.body().length);
                    exchange.getResponseBody().write(response.body());
                } catch (Exception e) {
                    log.warn("Catalog proxy forwarding failed", e);
                    try { exchange.sendResponseHeaders(502, -1); } catch (Exception ignored) { }
                } finally { exchange.close(); }
            });
            server.start();
        }
        int port() { return server.getAddress().getPort(); }
        Barrier pause(UUID operation, String action) { return barrier = new Barrier(operation, action); }
        @Override public void close() { if (barrier != null) barrier.resume.countDown(); server.stop(0); executor.shutdownNow(); }
        private static final class Barrier {
            final UUID operation;
            final String action;
            final java.util.concurrent.atomic.AtomicBoolean triggered = new java.util.concurrent.atomic.AtomicBoolean();
            final java.util.concurrent.CountDownLatch arrived = new java.util.concurrent.CountDownLatch(1), resume = new java.util.concurrent.CountDownLatch(1);
            volatile int status;
            Barrier(UUID operation, String action) { this.operation = operation; this.action = action; }
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
    @FunctionalInterface private interface Check { boolean get() throws Exception; }
    private static void await(Check check) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            if (check.get()) return;
            TimeUnit.MILLISECONDS.sleep(200);
        }
        fail("Timed out waiting for broker and allocation recovery");
    }
    private static HttpResponse<String> ownerRequest(String method, String url, String key) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("X-User-Id", "owner").header("X-User-Username", "owner");
        if (key != null) request.header("Idempotency-Key", key);
        return HttpClient.newHttpClient().send(request.method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> stripeWebhook(int port, String event, String type, String intent, String status) throws Exception {
        String body = "{\"id\":\"" + event + "\",\"object\":\"event\",\"type\":\"" + type
                + "\",\"data\":{\"object\":{\"object\":\"payment_intent\",\"id\":\"" + intent
                + "\",\"status\":\"" + status + "\",\"livemode\":false,\"amount\":1250,\"currency\":\"usd\"}}}";
        long timestamp = java.time.Instant.now().getEpochSecond();
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("whsec_cross_service_fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "t=" + timestamp + ",v1=" + java.util.HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/webhooks/stripe"))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json").header("Stripe-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> request(String method, String url, String token, String body) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15));
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (body != null) b.header("Content-Type", "application/json");
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(
                b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    static final class Keycloak {
        final String base;
        String admin;
        final String password;
        final ObjectMapper json = new ObjectMapper();
        Keycloak(String base, String password, String secret) throws Exception {
            this.base = base;
            this.password = password;
            refreshAdmin();
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
            String payment = api("GET", root + "/clients?clientId=payment-service", null).get(0).path("id").asText();
            api("POST", root + "/clients/" + payment + "/roles", "{\"name\":\"payment-reconciliation-read\"}");
            JsonNode readRole = api("GET", root + "/clients/" + payment + "/roles/payment-reconciliation-read", null);
            api("POST", root + "/users/" + user + "/role-mappings/clients/" + payment, "[" + readRole + "]");
            api("POST", root + "/clients/" + payment + "/roles", "{\"name\":\"payment-reconciliation-resolve\"}");
            JsonNode resolveRole = api("GET", root + "/clients/" + payment + "/roles/payment-reconciliation-resolve", null);
            api("POST", root + "/users/" + user + "/role-mappings/clients/" + payment, "[" + resolveRole + "]");
        }
        String token(String secret) throws Exception { return form("/realms/checkout-test/protocol/openid-connect/token", "grant_type=client_credentials&client_id=order-service&client_secret=" + secret).path("access_token").asText(); }
        void refreshAdmin() throws Exception { admin = form("/realms/master/protocol/openid-connect/token", "grant_type=password&client_id=admin-cli&username=admin&password=" + password).path("access_token").asText(); }
        String paymentRoleToken(String secret, String removed) throws Exception {
            refreshAdmin();
            String root = "/admin/realms/checkout-test";
            String order = api("GET", root + "/clients?clientId=order-service", null).get(0).path("id").asText();
            String user = api("GET", root + "/clients/" + order + "/service-account-user", null).path("id").asText();
            String payment = api("GET", root + "/clients?clientId=payment-service", null).get(0).path("id").asText();
            JsonNode role = api("GET", root + "/clients/" + payment + "/roles/" + removed, null);
            api("DELETE", root + "/users/" + user + "/role-mappings/clients/" + payment, "[" + role + "]");
            try { return token(secret); }
            finally { api("POST", root + "/users/" + user + "/role-mappings/clients/" + payment, "[" + role + "]"); }
        }
        String wrongPaymentAudienceToken(String secret) throws Exception {
            refreshAdmin();
            String root = "/admin/realms/checkout-test";
            String order = api("GET", root + "/clients?clientId=order-service", null).get(0).path("id").asText();
            JsonNode mappers = api("GET", root + "/clients/" + order + "/protocol-mappers/models", null);
            JsonNode mapper = null;
            for (JsonNode candidate : mappers) if ("payment-service-audience".equals(candidate.path("name").asText())) mapper = candidate;
            assertNotNull(mapper);
            String mapperId = mapper.path("id").asText();
            JsonNode scopes = api("GET", root + "/client-scopes", null);
            String rolesId = null;
            for (JsonNode scope : scopes) if ("roles".equals(scope.path("name").asText())) rolesId = scope.path("id").asText();
            assertNotNull(rolesId);
            api("DELETE", root + "/clients/" + order + "/protocol-mappers/models/" + mapperId, null);
            JsonNode roleMappers = api("GET", root + "/client-scopes/" + rolesId + "/protocol-mappers/models", null);
            JsonNode audienceMapper = null;
            for (JsonNode candidate : roleMappers) if ("oidc-audience-resolve-mapper".equals(candidate.path("protocolMapper").asText())) audienceMapper = candidate;
            assertNotNull(audienceMapper);
            api("DELETE", root + "/client-scopes/" + rolesId + "/protocol-mappers/models/" + audienceMapper.path("id").asText(), null);
            try { return token(secret); }
            finally {
                api("POST", root + "/client-scopes/" + rolesId + "/protocol-mappers/models", audienceMapper.toString());
                api("POST", root + "/clients/" + order + "/protocol-mappers/models", mapper.toString());
            }
        }
        String wrongRoleToken(String secret) throws Exception {
            refreshAdmin();
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
