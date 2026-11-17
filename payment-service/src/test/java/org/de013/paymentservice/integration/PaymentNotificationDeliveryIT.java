package org.de013.paymentservice.integration;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.*;

class PaymentNotificationDeliveryIT {
    static final String TOPIC = "notification.payment-email.v1";
    static final String SECRET = "whsec_artifact_fixture";

    @Test @Timeout(value = 12, unit = TimeUnit.MINUTES)
    void notificationCatchesUpAfterBeingOffline() throws Exception {
        Path temp = Path.of(System.getProperty("java.io.tmpdir"), "opencode");
        Files.createDirectories(temp);
        Path isolated = Files.createTempDirectory(temp, "notification-launcher-");
        String resource = Launcher.class.getName().replace('.', '/') + ".class";
        Path copy = isolated.resolve(resource); Files.createDirectories(copy.getParent());
        try (var in = Launcher.class.getClassLoader().getResourceAsStream(resource)) { assertNotNull(in); Files.copy(in, copy); }
        try (var paymentDb = new PostgreSQLContainer<>("postgres:17-alpine");
             var notificationDb = new PostgreSQLContainer<>("postgres:17-alpine");
             var kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
             var smtp = new Smtp()) {
            paymentDb.start(); notificationDb.start(); kafka.start();
            var fixture = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            AtomicInteger otherCalls = new AtomicInteger(), legacyCalls = new AtomicInteger(), userCalls = new AtomicInteger();
            fixture.createContext("/", x -> {
                String path = x.getRequestURI().getPath();
                String body;
                if (path.equals("/v1/account")) body = "{\"id\":\"acct_fixture\",\"object\":\"account\",\"charges_enabled\":true,\"payouts_enabled\":true,\"details_submitted\":true}";
                else if (path.equals("/notifications/send-email")) { legacyCalls.incrementAndGet(); body = "{\"success\":true,\"data\":{}}"; }
                else if (path.startsWith("/users/internal/by-keycloak-id/")) { userCalls.incrementAndGet(); body = "{\"success\":true,\"data\":{\"email\":\"owner@example.com\"}}"; }
                else { otherCalls.incrementAndGet(); body = "{\"success\":true,\"data\":{}}"; }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                x.getResponseHeaders().add("Content-Type", "application/json"); x.sendResponseHeaders(200, bytes.length);
                x.getResponseBody().write(bytes); x.close();
            }); fixture.start();
            String url = "http://127.0.0.1:" + fixture.getAddress().getPort();
            try (var payment = new App(temp, isolated, "payment-service", "org.de013.paymentservice.PaymentServiceApplication", paymentDb,
                    "stripe-base=" + url, "--payment.internal.issuer-uri=" + url,
                    "--payment.gateways.stripe.api-key=sk_test_fixture", "--payment.gateways.stripe.webhook-secret=" + SECRET,
                    "--payment.events.enabled=true", "--payment.notifications.events.enabled=true",
                    "--payment.notifications.events.relay-delay-ms=500", "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                    "--spring.cloud.openfeign.client.config.order-service.url=" + url,
                    "--spring.cloud.openfeign.client.config.notification-service.url=" + url,
                    "--spring.cloud.openfeign.client.config.user-service.url=" + url);
                 var db = DriverManager.getConnection(paymentDb.getJdbcUrl(), paymentDb.getUsername(), paymentDb.getPassword());
                 var consumer = new KafkaConsumer<String, String>(Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                         ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(), ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                         ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class, ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
                long id = number(db, "INSERT INTO payments(payment_number,order_id,user_id,amount,currency,status,method,gateway_name,stripe_payment_intent_id,stripe_intent_livemode,stripe_intent_created_by_service,receipt_email) VALUES ('artifact-pay',101,'owner',12.50,'USD','PENDING','CARD','STRIPE','pi_artifact',false,true,'owner@example.com') RETURNING id");
                var response = webhook(payment.port, "artifact");
                assertEquals(200, response.statusCode(), response.body());
                assertEquals("SUCCEEDED", text(db, "SELECT status FROM payments WHERE id=" + id));
                await(() -> "PUBLISHED".equals(text(db, "SELECT state FROM payment_notification_outbox WHERE payment_id=" + id)));
                String payload = text(db, "SELECT payload FROM payment_notification_outbox WHERE payment_id=" + id);
                consumer.subscribe(List.of(TOPIC));
                ConsumerRecord<String,String> captured = null;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                while (captured == null && System.nanoTime() < deadline) for (var record : consumer.poll(Duration.ofMillis(500))) captured = record;
                assertNotNull(captured, "No broker record"); assertEquals(Long.toString(id), captured.key()); assertEquals(payload, captured.value());
                assertEquals(0, otherCalls.get(), "Unexpected Feign call"); assertEquals(0, legacyCalls.get()); assertEquals(0, userCalls.get());
                long offId;
                try (var notification = new App(temp, isolated, "notification-service", "org.de013.notificationservice.NotificationServiceApplication", notificationDb,
                        "--notification.payment-email.events.enabled=true", "--notification.payment-email.poll-ms=500",
                        "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(), "--spring.mail.host=127.0.0.1", "--spring.mail.port=" + smtp.port(),
                        "--spring.mail.properties.mail.smtp.auth=false", "--spring.mail.properties.mail.smtp.starttls.enable=false");
                     var ndb = DriverManager.getConnection(notificationDb.getJdbcUrl(), notificationDb.getUsername(), notificationDb.getPassword())) {
                    await(() -> number(ndb, "SELECT count(*) FROM payment_email_request WHERE delivery_status='SENT'") == 1);
                    assertEquals(1, number(ndb, "SELECT count(*) FROM payment_email_request"));
                    assertEquals(1, number(ndb, "SELECT count(*) FROM notifications"));
                    assertEquals(1, smtp.accepted.get());
                    assertEquals(0, otherCalls.get()); assertEquals(0, legacyCalls.get()); assertEquals(0, userCalls.get());
                }
                long attempts;
                try (var ndb = DriverManager.getConnection(notificationDb.getJdbcUrl(), notificationDb.getUsername(), notificationDb.getPassword())) {
                    attempts = number(ndb, "SELECT attempts FROM payment_email_request");
                }
                var producerConfig = Map.<String,Object>of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(), ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class, ProducerConfig.ACKS_CONFIG, "all");
                try (var notification = new App(temp, isolated, "notification-service", "org.de013.notificationservice.NotificationServiceApplication", notificationDb,
                        "--notification.payment-email.events.enabled=true", "--notification.payment-email.poll-ms=500",
                        "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(), "--spring.mail.host=127.0.0.1", "--spring.mail.port=" + smtp.port(),
                        "--spring.mail.properties.mail.smtp.auth=false", "--spring.mail.properties.mail.smtp.starttls.enable=false");
                     var ndb = DriverManager.getConnection(notificationDb.getJdbcUrl(), notificationDb.getUsername(), notificationDb.getPassword());
                     var admin = AdminClient.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
                    RecordMetadata replay;
                    try (var producer = new KafkaProducer<String,String>(producerConfig)) { replay = producer.send(new ProducerRecord<>(TOPIC, captured.key(), captured.value())).get(30, TimeUnit.SECONDS); }
                    var partition = new TopicPartition(TOPIC, replay.partition());
                    await(() -> { var offsets = admin.listConsumerGroupOffsets("notification-payment-email-v1").partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS); return offsets.containsKey(partition) && offsets.get(partition).offset() > replay.offset(); });
                    assertEquals(1, number(ndb, "SELECT count(*) FROM payment_email_request"));
                    assertEquals(1, number(ndb, "SELECT count(*) FROM notifications"));
                    assertEquals(attempts, number(ndb, "SELECT attempts FROM payment_email_request")); assertEquals(1, smtp.accepted.get());
                }
                try (var notification = new App(temp, isolated, "notification-service", "org.de013.notificationservice.NotificationServiceApplication", notificationDb,
                        "--notification.payment-email.events.enabled=false", "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                        "--spring.mail.host=127.0.0.1", "--spring.mail.port=" + smtp.port());
                     var ndb = DriverManager.getConnection(notificationDb.getJdbcUrl(), notificationDb.getUsername(), notificationDb.getPassword())) {
                    assertEquals("0/0/0", notification.paymentEmailBeans, "Disabled runtime must not create worker, configuration, or listener container");
                    offId = seed(db, "no_consumer", 102, true);
                    assertEquals(200, webhook(payment.port, "no_consumer").statusCode());
                    await(() -> "PUBLISHED".equals(text(db, "SELECT state FROM payment_notification_outbox WHERE payment_id=" + offId)));
                    String offPayload = text(db, "SELECT payload FROM payment_notification_outbox WHERE payment_id=" + offId);
                    assertEquals(offPayload, findRecord(consumer, Long.toString(offId)).value());
                    assertEquals(0, number(ndb, "SELECT count(*) FROM payment_email_request WHERE payment_id=" + offId));
                    assertEquals(1, smtp.accepted.get());
                }
                try (var ndb = DriverManager.getConnection(notificationDb.getJdbcUrl(), notificationDb.getUsername(), notificationDb.getPassword())) {
                    assertEquals(0, number(ndb, "SELECT count(*) FROM payment_email_request WHERE payment_id=" + offId));
                    assertEquals(1, smtp.accepted.get(), "Disabled JVM must not deliver before or during shutdown");
                }
                var beforeHolds = consumer.endOffsets(consumer.assignment());
                long missing = seed(db, "missing", 103, false);
                assertEquals(200, webhook(payment.port, "missing").statusCode());
                assertEquals("RECONCILIATION", text(db, "SELECT state FROM payment_notification_outbox WHERE payment_id=" + missing));
                long held = seed(db, "held_before", 104, true);
                execute(db, "INSERT INTO payment_order_guard(order_id,reconciliation_hold) VALUES (104,true)");
                assertEquals(200, webhook(payment.port, "held_before").statusCode());
                assertEquals("SUCCEEDED", text(db, "SELECT status FROM payments WHERE id=" + held));
                assertEquals(0, number(db, "SELECT count(*) FROM payment_notification_outbox WHERE payment_id=" + held));
                long heldAfter = seed(db, "held_after", 105, true);
                // Inject guard state in the enqueue transaction; this is not a real late callback.
                execute(db, "CREATE FUNCTION test_notification_hold() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.payment_id = " + heldAfter + " THEN UPDATE payment_order_guard SET reconciliation_hold=true WHERE order_id=105; END IF; RETURN NEW; END $$");
                execute(db, "CREATE TRIGGER test_notification_hold AFTER INSERT ON payment_notification_outbox FOR EACH ROW EXECUTE FUNCTION test_notification_hold()");
                try {
                    assertEquals(200, webhook(payment.port, "held_after").statusCode());
                    await(() -> "RECONCILIATION".equals(text(db, "SELECT state FROM payment_notification_outbox WHERE payment_id=" + heldAfter)));
                    assertEquals(1, number(db, "SELECT count(*) FROM payment_order_guard WHERE order_id=105 AND reconciliation_hold=true"));
                } finally { execute(db, "DROP TRIGGER test_notification_hold ON payment_notification_outbox"); execute(db, "DROP FUNCTION test_notification_hold()"); }
                assertEquals(beforeHolds, consumer.endOffsets(consumer.assignment()), "Missing recipient and held payments must not publish");
                assertEquals(0, legacyCalls.get()); assertEquals(0, userCalls.get()); assertEquals(0, otherCalls.get()); assertEquals(1, smtp.accepted.get());
                payment.close();
                try (var legacyPayment = new App(temp, isolated, "payment-service", "org.de013.paymentservice.PaymentServiceApplication", paymentDb,
                        "stripe-base=" + url, "--payment.internal.issuer-uri=" + url,
                        "--payment.gateways.stripe.api-key=sk_test_fixture", "--payment.gateways.stripe.webhook-secret=" + SECRET,
                        "--payment.events.enabled=true", "--payment.notifications.events.enabled=false", "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                        "--spring.cloud.openfeign.client.config.order-service.url=" + url,
                        "--spring.cloud.openfeign.client.config.notification-service.url=" + url,
                        "--spring.cloud.openfeign.client.config.user-service.url=" + url)) {
                    for (boolean enabled : List.of(true, false)) {
                        try (var notification = new App(temp, isolated, "notification-service", "org.de013.notificationservice.NotificationServiceApplication", notificationDb,
                                "--notification.payment-email.events.enabled=" + enabled, "--notification.payment-email.poll-ms=500",
                                "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(), "--spring.mail.host=127.0.0.1", "--spring.mail.port=" + smtp.port(),
                                "--spring.mail.properties.mail.smtp.auth=false", "--spring.mail.properties.mail.smtp.starttls.enable=false");
                             var ndb = DriverManager.getConnection(notificationDb.getJdbcUrl(), notificationDb.getUsername(), notificationDb.getPassword())) {
                            if (enabled) await(() -> number(ndb, "SELECT count(*) FROM payment_email_request WHERE payment_id=" + offId + " AND delivery_status='SENT'") == 1);
                            int smtpBefore = smtp.accepted.get(), callsBefore = legacyCalls.get();
                            String suffix = enabled ? "legacy_on" : "legacy_off";
                            long legacyId = seed(db, suffix, enabled ? 106 : 107, true);
                            var partitions = consumer.assignment(); var before = consumer.endOffsets(partitions);
                            assertEquals(200, webhook(legacyPayment.port, suffix).statusCode());
                            assertEquals(callsBefore + 1, legacyCalls.get(), "Actual legacy Feign request required");
                            assertEquals("SUCCEEDED", text(db, "SELECT status FROM payments WHERE id=" + legacyId));
                            assertEquals(0, number(db, "SELECT count(*) FROM payment_notification_outbox WHERE payment_id=" + legacyId));
                            assertEquals(before, consumer.endOffsets(partitions));
                            assertEquals(0, number(ndb, "SELECT count(*) FROM payment_email_request WHERE payment_id=" + legacyId));
                            assertEquals(smtpBefore, smtp.accepted.get()); assertEquals(0, userCalls.get()); assertEquals(0, otherCalls.get());
                        }
                    }
                }
            } finally { fixture.stop(0); }
        }
    }

    static HttpResponse<String> webhook(int port, String suffix) throws Exception {
        String body = "{\"id\":\"evt_" + suffix + "\",\"object\":\"event\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"object\":\"payment_intent\",\"id\":\"pi_" + suffix + "\",\"status\":\"succeeded\",\"livemode\":false,\"amount\":1250,\"currency\":\"usd\"}}}";
        long t = Instant.now().getEpochSecond(); Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "t=" + t + ",v1=" + HexFormat.of().formatHex(mac.doFinal((t + "." + body).getBytes(StandardCharsets.UTF_8)));
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/webhooks/stripe"))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json").header("Stripe-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    static String text(Connection db, String sql) throws Exception { try (var s = db.createStatement(); var r = s.executeQuery(sql)) { assertTrue(r.next(), sql); return r.getString(1); } }
    static long number(Connection db, String sql) throws Exception { return Long.parseLong(text(db, sql)); }
    static void execute(Connection db, String sql) throws Exception { try (var statement = db.createStatement()) { statement.execute(sql); } }
    static long seed(Connection db, String suffix, long order, boolean receipt) throws Exception {
        return number(db, "INSERT INTO payments(payment_number,order_id,user_id,amount,currency,status,method,gateway_name,stripe_payment_intent_id,stripe_intent_livemode,stripe_intent_created_by_service,receipt_email) VALUES ('pay_" + suffix + "'," + order + ",'owner',12.50,'USD','PENDING','CARD','STRIPE','pi_" + suffix + "',false,true," + (receipt ? "'owner@example.com'" : "NULL") + ") RETURNING id");
    }
    static ConsumerRecord<String,String> findRecord(KafkaConsumer<String,String> consumer, String key) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) for (var record : consumer.poll(Duration.ofMillis(500))) if (key.equals(record.key())) return record;
        throw new AssertionError("Missing broker record for " + key);
    }
    interface Check { boolean ok() throws Exception; }
    static void await(Check check) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < end) { if (check.ok()) return; Thread.sleep(200); }
        fail("Timed out waiting for condition");
    }

    public static class Launcher {
        public static void main(String[] args) throws Exception {
            ClassLoader l = Thread.currentThread().getContextClassLoader();
            if (System.getProperty("test.stripe.base") != null) l.loadClass("com.stripe.Stripe").getMethod("overrideApiBase", String.class).invoke(null, System.getProperty("test.stripe.base"));
            Object ctx = l.loadClass("org.springframework.boot.SpringApplication").getMethod("run", Class.class, String[].class)
                    .invoke(null, l.loadClass(args[0]), Arrays.copyOfRange(args, 1, args.length));
            Object server = l.loadClass("org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext").getMethod("getWebServer").invoke(ctx);
            int port = (int) l.loadClass("org.springframework.boot.web.server.WebServer").getMethod("getPort").invoke(server);
            if (args[0].contains("notificationservice")) {
                var names = ctx.getClass().getMethod("getBeanNamesForType", Class.class);
                int workers = ((String[]) names.invoke(ctx, l.loadClass("org.de013.notificationservice.service.PaymentEmailWorkers"))).length;
                int configs = ((String[]) names.invoke(ctx, l.loadClass("org.de013.notificationservice.config.PaymentEmailKafkaConfig"))).length;
                int containers = 0;
                String[] registries = (String[]) names.invoke(ctx, l.loadClass("org.springframework.kafka.config.KafkaListenerEndpointRegistry"));
                if (registries.length > 0) {
                    Object registry = ctx.getClass().getMethod("getBean", String.class).invoke(ctx, registries[0]);
                    containers = ((java.util.Collection<?>) registry.getClass().getMethod("getListenerContainers").invoke(registry)).size();
                }
                org.slf4j.LoggerFactory.getLogger(Launcher.class).info("TEST_PAYMENT_EMAIL_BEANS {}/{}/{}", workers, configs, containers);
            }
            org.slf4j.LoggerFactory.getLogger(Launcher.class).info("TEST_READY {}", port);
            while (System.in.read() != -1) { }
            ctx.getClass().getMethod("close").invoke(ctx);
        }
    }
    static class App implements AutoCloseable {
        final Process process; final int port; final Path log; volatile String paymentEmailBeans; final ExecutorService reader = Executors.newSingleThreadExecutor();
        App(Path temp, Path isolated, String module, String main, PostgreSQLContainer<?> db, String... extra) throws Exception {
            Path jar = Path.of("..", module, "target", module + "-0.0.1-SNAPSHOT.jar").toAbsolutePath();
            assertTrue(Files.isRegularFile(jar), "Missing artifact " + jar);
            var time = Files.getLastModifiedTime(jar);
            for (Path root : List.of(Path.of("..", module, "src", "main"), Path.of("..", "common", "src", "main")))
                try (var files = Files.walk(root)) { for (Path f : files.filter(Files::isRegularFile).toList()) assertTrue(Files.getLastModifiedTime(f).compareTo(time) <= 0, "Stale artifact: " + f); }
            for (Path f : List.of(Path.of("..", "pom.xml"), Path.of("..", "common", "pom.xml"), Path.of("..", module, "pom.xml"))) assertTrue(Files.getLastModifiedTime(f).compareTo(time) <= 0, "Stale artifact: " + f);
            log = Files.createTempFile(temp, "notification-artifact-" + module, ".log");
            var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Dloader.path=" + isolated, "-Dloader.main=" + Launcher.class.getName()));
            for (String e : extra) if (e.startsWith("stripe-base=")) command.add("-Dtest.stripe.base=" + e.substring(12));
            command.addAll(List.of("-cp", jar.toString(), "org.springframework.boot.loader.launch.PropertiesLauncher", main,
                    "--server.port=0", "--spring.cloud.config.enabled=false", "--spring.cloud.bus.enabled=false", "--eureka.client.enabled=false",
                    "--spring.jpa.hibernate.ddl-auto=validate", "--spring.flyway.enabled=true", "--management.health.defaults.enabled=false",
                    "--spring.datasource.url=" + db.getJdbcUrl(), "--spring.datasource.username=" + db.getUsername(), "--spring.datasource.password=" + db.getPassword()));
            for (String e : extra) if (!e.startsWith("stripe-base=")) command.add(e);
            process = new ProcessBuilder(command).redirectErrorStream(true).start(); var ready = new CompletableFuture<Integer>();
            reader.submit(() -> { try (var input = new BufferedReader(new InputStreamReader(process.getInputStream())); var out = Files.newBufferedWriter(log)) {
                String line; while ((line = input.readLine()) != null) { out.write(line); out.newLine(); out.flush(); int beans = line.indexOf("TEST_PAYMENT_EMAIL_BEANS "); if (beans >= 0) paymentEmailBeans = line.substring(beans + "TEST_PAYMENT_EMAIL_BEANS ".length()).trim(); int marker = line.indexOf("TEST_READY "); if (marker >= 0) ready.complete(Integer.parseInt(line.substring(marker + 11).trim())); }
                ready.completeExceptionally(new IllegalStateException("Process exited: " + log));
            } catch (Exception e) { ready.completeExceptionally(e); } });
            try { port = ready.get(120, TimeUnit.SECONDS); } catch (Exception e) { close(); throw new IllegalStateException("Startup failed: " + log, e); }
        }
        public void close() throws Exception { try { process.getOutputStream().close(); if (!process.waitFor(15, TimeUnit.SECONDS)) { process.destroyForcibly(); assertTrue(process.waitFor(15, TimeUnit.SECONDS), "JVM did not terminate: " + log); } } finally { reader.shutdownNow(); } }
    }
    static class Smtp implements AutoCloseable {
        final ServerSocket server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        final AtomicInteger accepted = new AtomicInteger(); final ExecutorService pool = Executors.newSingleThreadExecutor();
        Smtp() throws IOException { pool.submit(() -> { while (!server.isClosed()) try (var socket = server.accept(); var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)); var out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.US_ASCII)) {
            out.print("220 localhost ESMTP\r\n"); out.flush(); String line; boolean data = false;
            while ((line = in.readLine()) != null) {
                if (data) { if (line.equals(".")) { accepted.incrementAndGet(); data = false; out.print("250 accepted\r\n"); out.flush(); } continue; }
                if (line.startsWith("DATA")) { data = true; out.print("354 end with dot\r\n"); }
                else if (line.startsWith("QUIT")) { out.print("221 bye\r\n"); out.flush(); break; }
                else out.print("250 OK\r\n"); out.flush();
            }
        } catch (IOException e) { if (!server.isClosed()) throw new UncheckedIOException(e); } }); }
        int port() { return server.getLocalPort(); }
        public void close() throws Exception { server.close(); pool.shutdownNow(); pool.awaitTermination(5, TimeUnit.SECONDS); }
    }
}
