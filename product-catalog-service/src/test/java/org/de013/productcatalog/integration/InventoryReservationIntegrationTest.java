package org.de013.productcatalog.integration;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import org.de013.productcatalog.dto.inventory.ReservationRequest;
import org.de013.productcatalog.dto.inventory.ReservationRequest.TargetKind;
import org.de013.productcatalog.entity.Inventory;
import org.de013.productcatalog.entity.Product;
import org.de013.productcatalog.entity.ProductVariant;
import org.de013.productcatalog.entity.VariantInventory;
import org.de013.productcatalog.entity.enums.VariantType;
import org.de013.productcatalog.exception.ReservationException;
import org.de013.productcatalog.service.InventoryReservationService;
import org.de013.productcatalog.service.InventoryService;
import org.de013.productcatalog.service.VariantInventoryService;
import org.de013.productcatalog.service.ProductVariantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InventoryReservationIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
    @Autowired InventoryReservationService service;
    @Autowired InventoryService inventoryService;
    @Autowired VariantInventoryService variantInventoryService;
    @Autowired ProductVariantService productVariantService;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired MockMvc mvc;
    @MockBean(name = "cacheManager") CacheManager cache;
    long product, variant;

    @BeforeEach void setup() {
        jdbc.execute("DROP TABLE IF EXISTS inventory_reservations");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V11__Create_inventory_reservations.sql")).execute(dataSource);
        jdbc.execute("DROP FUNCTION IF EXISTS protect_reserved_inventory_deletion() CASCADE");
        jdbc.execute("""
                CREATE FUNCTION protect_reserved_inventory_deletion() RETURNS trigger AS $$
                DECLARE kind text; target bigint;
                BEGIN
                    IF TG_TABLE_NAME = 'inventory' THEN kind := 'PRODUCT'; target := OLD.product_id;
                    ELSE kind := 'VARIANT'; target := OLD.variant_id; END IF;
                    IF EXISTS (SELECT 1 FROM inventory_reservations WHERE target_kind=kind AND target_id=target AND state='RESERVED') THEN
                        RAISE EXCEPTION 'Active reservation prevents inventory deletion' USING ERRCODE='23514', CONSTRAINT='active_inventory_reservation';
                    END IF;
                    RETURN OLD;
                END; $$ LANGUAGE plpgsql
                """);
        jdbc.execute("CREATE TRIGGER protect_product_inventory_deletion BEFORE DELETE ON inventory FOR EACH ROW EXECUTE FUNCTION protect_reserved_inventory_deletion()");
        jdbc.execute("CREATE TRIGGER protect_variant_inventory_deletion BEFORE DELETE ON variant_inventory FOR EACH ROW EXECUTE FUNCTION protect_reserved_inventory_deletion()");
        new TransactionTemplate(manager).executeWithoutResult(s -> {
            Product p = Product.builder().name("Reservation fixture").sku("RS-" + UUID.randomUUID()).price(BigDecimal.TEN).build();
            em.persist(p);
            ProductVariant v = ProductVariant.builder().product(p).variantType(VariantType.SIZE).name("Size").value("M").build();
            em.persist(v);
            em.persist(Inventory.builder().product(p).quantity(10).build());
            em.persist(VariantInventory.builder().product(p).variant(v).quantity(10).build());
            em.flush(); product = p.getId(); variant = v.getId();
        });
    }
    ReservationRequest request(TargetKind kind, long id, int quantity) {
        return new ReservationRequest(UUID.randomUUID(), kind, id, quantity, "attempt");
    }
    void stock(TargetKind kind, long id, int quantity, int reserved) {
        String table = kind == TargetKind.PRODUCT ? "inventory" : "variant_inventory";
        String key = kind == TargetKind.PRODUCT ? "product_id" : "variant_id";
        var values = jdbc.queryForMap("SELECT quantity,reserved_quantity FROM " + table + " WHERE " + key + "=?", id);
        assertEquals(quantity, values.get("quantity")); assertEquals(reserved, values.get("reserved_quantity"));
    }
    @Test void poolsReplaysAndTerminals() {
        for (TargetKind kind : TargetKind.values()) {
            long id = kind == TargetKind.PRODUCT ? product : variant;
            var r = request(kind, id, 3);
            assertEquals("RESERVED", service.reserve(r).state());
            assertEquals("RESERVED", service.reserve(r).state()); stock(kind, id, 10, 3);
            assertThrows(ReservationException.class, () -> service.reserve(new ReservationRequest(r.operationId(), kind, id, 2, r.owner())));
            assertEquals("RELEASED", service.terminal(r.operationId(), false).state());
            assertEquals("RELEASED", service.terminal(r.operationId(), false).state()); stock(kind, id, 10, 0);
            assertThrows(ReservationException.class, () -> service.terminal(r.operationId(), true));
            assertThrows(ReservationException.class, () -> service.reserve(r));
            var f = request(kind, id, 3); service.reserve(f);
            assertEquals("FULFILLED", service.terminal(f.operationId(), true).state()); stock(kind, id, 7, 0);
            assertEquals("FULFILLED", service.terminal(f.operationId(), true).state());
            assertThrows(ReservationException.class, () -> service.terminal(f.operationId(), false));
        }
    }
    @Test void rejectionRemainsAfterRestockAndMissingTarget() {
        var r = request(TargetKind.PRODUCT, product, 11);
        assertEquals("INSUFFICIENT_STOCK", service.reserve(r).reason());
        jdbc.update("UPDATE inventory SET quantity=20 WHERE product_id=?", product);
        assertEquals("REJECTED", service.reserve(r).state()); stock(TargetKind.PRODUCT, product, 20, 0);
        assertThrows(ReservationException.class, () -> service.terminal(r.operationId(), false));
        assertEquals("TARGET_NOT_FOUND", service.reserve(request(TargetKind.PRODUCT, Long.MAX_VALUE, 1)).reason());
        assertEquals("OPERATION_NOT_FOUND", assertThrows(ReservationException.class, () -> service.lookup(UUID.randomUUID())).getErrorCode());
    }
    @Test void competingClaimsAndTerminalActions() throws Exception {
        var a = request(TargetKind.PRODUCT, product, 7); var b = request(TargetKind.PRODUCT, product, 7);
        var pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch gate = new CountDownLatch(1);
            var x = pool.submit(() -> { gate.await(); return service.reserve(a); });
            var y = pool.submit(() -> { gate.await(); return service.reserve(b); });
            gate.countDown(); var rx = x.get(20, TimeUnit.SECONDS); var ry = y.get(20, TimeUnit.SECONDS);
            assertNotEquals(rx.state(), ry.state()); stock(TargetKind.PRODUCT, product, 10, 7);
            UUID winner = rx.state().equals("RESERVED") ? a.operationId() : b.operationId();
            CountDownLatch next = new CountDownLatch(1);
            var release = pool.submit(() -> { next.await(); try { return service.terminal(winner, false).state(); } catch (ReservationException e) { return "CONFLICT"; } });
            var fulfill = pool.submit(() -> { next.await(); try { return service.terminal(winner, true).state(); } catch (ReservationException e) { return "CONFLICT"; } });
            next.countDown(); var one = release.get(20, TimeUnit.SECONDS); var two = fulfill.get(20, TimeUnit.SECONDS);
            assertTrue(one.equals("CONFLICT") ^ two.equals("CONFLICT"));
            stock(TargetKind.PRODUCT, product, one.equals("RELEASED") ? 10 : 3, 0);
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS)); }
    }
    @Test void rollbackLeavesNoLedgerOrStock() {
        var r = request(TargetKind.PRODUCT, product, 3);
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(manager).executeWithoutResult(s -> {
            service.reserve(r); throw new IllegalStateException("injected rollback");
        }));
        stock(TargetKind.PRODUCT, product, 10, 0);
        assertThrows(ReservationException.class, () -> service.lookup(r.operationId()));
    }

    @Test void httpValidationAndStatusContract() throws Exception {
        String base = "/internal/inventory-reservations";
        String id = UUID.randomUUID().toString();
        String valid = "{\"operationId\":\"" + id + "\",\"targetKind\":\"PRODUCT\",\"targetId\":" + product + ",\"quantity\":3,\"owner\":\"attempt\"}";
        String[] invalid = {
                valid.replace(id, "bad-uuid"), valid.replace("\"" + id + "\"", "null"),
                valid.replace("PRODUCT", "OTHER"), valid.replace("\"PRODUCT\"", "null"),
                valid.replace("\"targetId\":" + product, "\"targetId\":0"),
                valid.replace("\"targetId\":" + product, "\"targetId\":null"),
                valid.replace("\"quantity\":3", "\"quantity\":0"), valid.replace("\"quantity\":3", "\"quantity\":null"),
                valid.replace("attempt", "  "), valid.replace("attempt", "x".repeat(256)),
                valid.replace("\"owner\":\"attempt\"", "\"owner\":null")
        };
        for (String body : invalid) {
            mvc.perform(post(base).contentType("application/json").content(body)).andExpect(status().isBadRequest());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM inventory_reservations", Integer.class));
        }
        mvc.perform(post(base).contentType("application/json").content(valid)).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RESERVED")).andExpect(jsonPath("$.operationId").value(id));
        mvc.perform(post(base).contentType("application/json").content(valid.replace("\"quantity\":3", "\"quantity\":4")))
                .andExpect(status().isConflict());
        mvc.perform(get(base + "/" + UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OPERATION_NOT_FOUND"));
    }

    @Test void concurrentSameOperationOnlyReservesOnce() throws Exception {
        var r = request(TargetKind.PRODUCT, product, 3);
        var pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch gate = new CountDownLatch(1);
            var a = pool.submit(() -> { gate.await(); return service.reserve(r); });
            var b = pool.submit(() -> { gate.await(); return service.reserve(r); });
            gate.countDown();
            assertEquals(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
            stock(TargetKind.PRODUCT, product, 10, 3);
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM inventory_reservations", Integer.class));
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS)); }
    }

    @Test void missingTargetRejectionSurvivesCreation() {
        jdbc.update("DELETE FROM inventory WHERE product_id=?", product);
        var rejected = request(TargetKind.PRODUCT, product, 3);
        assertEquals("TARGET_NOT_FOUND", service.reserve(rejected).reason());
        jdbc.update("INSERT INTO inventory (product_id, quantity, reserved_quantity, track_inventory, allow_backorder, created_at, updated_at) VALUES (?, 10, 0, true, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", product);
        assertEquals("TARGET_NOT_FOUND", service.reserve(rejected).reason());
        assertEquals("RESERVED", service.reserve(request(TargetKind.PRODUCT, product, 3)).state());
        stock(TargetKind.PRODUCT, product, 10, 3);
    }

    @Test void terminalSqlFailureRollsBackStockAndState() {
        var r = request(TargetKind.PRODUCT, product, 3);
        service.reserve(r);
        jdbc.execute("CREATE OR REPLACE FUNCTION fail_reservation_terminal() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.state = 'FULFILLED' THEN RAISE EXCEPTION 'injected terminal failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER fail_reservation_terminal BEFORE UPDATE ON inventory_reservations FOR EACH ROW EXECUTE FUNCTION fail_reservation_terminal()");
        try {
            assertThrows(org.springframework.dao.DataAccessException.class, () -> service.terminal(r.operationId(), true));
            stock(TargetKind.PRODUCT, product, 10, 3);
            assertEquals("RESERVED", service.lookup(r.operationId()).state());
        } finally {
            jdbc.execute("DROP TRIGGER fail_reservation_terminal ON inventory_reservations");
            jdbc.execute("DROP FUNCTION fail_reservation_terminal()");
        }
        assertEquals("FULFILLED", service.terminal(r.operationId(), true).state());
        assertEquals("FULFILLED", service.terminal(r.operationId(), true).state());
        stock(TargetKind.PRODUCT, product, 7, 0);
    }

    @Test void legacyCannotConsumeDurableReservations() {
        for (TargetKind kind : TargetKind.values()) {
            long id = kind == TargetKind.PRODUCT ? product : variant;
            var r = request(kind, id, 3);
            service.reserve(r);
            if (kind == TargetKind.PRODUCT) {
                assertThrows(ReservationException.class, () -> inventoryService.releaseReservedStock(id, 1));
                assertThrows(ReservationException.class, () -> inventoryService.fulfillOrder(id, 1));
            } else {
                assertThrows(ReservationException.class, () -> variantInventoryService.releaseReservedStock(id, 1));
                assertThrows(ReservationException.class, () -> variantInventoryService.fulfillOrder(id, 1));
            }
            stock(kind, id, 10, 3);
            service.terminal(r.operationId(), false);
        }
    }

    @Test void activeInventoryCannotBeDeletedButTerminalHistorySurvives() {
        for (TargetKind kind : TargetKind.values()) {
            long id = kind == TargetKind.PRODUCT ? product : variant;
            var r = request(kind, id, 3);
            service.reserve(r);
            String table = kind == TargetKind.PRODUCT ? "inventory" : "variant_inventory";
            String key = kind == TargetKind.PRODUCT ? "product_id" : "variant_id";
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM " + table + " WHERE " + key + "=?", id));
            stock(kind, id, 10, 3);
            service.terminal(r.operationId(), kind == TargetKind.VARIANT);
            assertEquals(1, jdbc.update("DELETE FROM " + table + " WHERE " + key + "=?", id));
            assertEquals(kind == TargetKind.PRODUCT ? "RELEASED" : "FULFILLED", service.lookup(r.operationId()).state());
        }
    }

    @Test void flywayMigratesFreshPostgresSchemaThroughV12() {
        String schema = "catalog_flyway_" + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").cleanDisabled(false).load();
        try {
            var result = flyway.migrate();
            assertEquals(12, result.migrationsExecuted);
            assertEquals("12", flyway.info().current().getVersion().toString());
        } finally { flyway.clean(); }
    }

    @Test void realFlywayDeleteReserveBothLockOrderings() throws Exception {
        String schema = "catalog_race_" + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").cleanDisabled(false).load();
        var executor = Executors.newSingleThreadExecutor();
        TransactionTemplate tx = new TransactionTemplate(manager);
        try {
            flyway.migrate();
            for (TargetKind kind : TargetKind.values()) {
                for (boolean reserveFirst : new boolean[]{true, false}) {
                    long[] ids = tx.execute(status -> {
                        jdbc.execute("SET LOCAL search_path TO " + schema);
                        long p = jdbc.queryForObject("INSERT INTO products (name, sku, price) VALUES (?, ?, 10) RETURNING id", Long.class, "Race", UUID.randomUUID().toString());
                        long v = jdbc.queryForObject("INSERT INTO product_variants (product_id, variant_type, name, value) VALUES (?, 'SIZE', 'Size', 'M') RETURNING id", Long.class, p);
                        jdbc.update("UPDATE inventory SET quantity=10 WHERE product_id=?", p);
                        jdbc.update("UPDATE variant_inventory SET quantity=10 WHERE variant_id=?", v);
                        return new long[]{p, v};
                    });
                    long id = kind == TargetKind.PRODUCT ? ids[0] : ids[1];
                    String table = kind == TargetKind.PRODUCT ? "inventory" : "variant_inventory";
                    String key = kind == TargetKind.PRODUCT ? "product_id" : "variant_id";
                    ReservationRequest request = request(kind, id, 3);
                    CompletableFuture<Integer> pid = new CompletableFuture<>();
                    @SuppressWarnings("unchecked") Future<Object>[] pending = new Future[1];
                    tx.executeWithoutResult(status -> {
                        jdbc.execute("SET LOCAL search_path TO " + schema);
                        if (reserveFirst) service.reserve(request);
                        else assertEquals(1, jdbc.update("DELETE FROM " + table + " WHERE " + key + "=?", id));
                        pending[0] = executor.submit(() -> tx.execute(inner -> {
                            jdbc.execute("SET LOCAL search_path TO " + schema);
                            jdbc.execute("SET LOCAL lock_timeout = '15s'");
                            pid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                            return reserveFirst ? jdbc.update("DELETE FROM " + table + " WHERE " + key + "=?", id) : service.reserve(request);
                        }));
                        try {
                            int workerPid = pid.get(10, TimeUnit.SECONDS);
                            await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(20)).until(() -> {
                                jdbc.execute("SELECT pg_stat_clear_snapshot()");
                                return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock')", Boolean.class, workerPid));
                            });
                        } catch (Exception ex) { throw new AssertionError(ex); }
                    });
                    if (reserveFirst) {
                        var error = assertThrows(java.util.concurrent.ExecutionException.class, () -> pending[0].get(20, TimeUnit.SECONDS));
                        assertTrue(error.getCause() instanceof org.springframework.dao.DataIntegrityViolationException, error.toString());
                        assertTrue(error.getCause().getMessage().contains("active_inventory_reservation") || error.getCause().getMessage().contains("Active reservation"));
                    } else {
                        var outcome = (org.de013.productcatalog.dto.inventory.ReservationResponse) pending[0].get(20, TimeUnit.SECONDS);
                        assertEquals("REJECTED", outcome.state()); assertEquals("TARGET_NOT_FOUND", outcome.reason());
                    }
                    tx.executeWithoutResult(status -> {
                        jdbc.execute("SET LOCAL search_path TO " + schema);
                        assertEquals(reserveFirst ? 1 : 0, jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + key + "=?", Integer.class, id));
                        assertEquals(reserveFirst ? "RESERVED" : "REJECTED", service.lookup(request.operationId()).state());
                    });
                }
            }
        } finally {
            executor.shutdownNow(); assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
            flyway.clean();
        }
    }

    @Test void productDeleteEndpointPreservesActiveReservationAndAllowsTerminalDeletion() throws Exception {
        var localCaches = new org.springframework.cache.concurrent.ConcurrentMapCacheManager();
        when(cache.getCache(anyString())).thenAnswer(call -> localCaches.getCache(call.getArgument(0)));
        String schema = "catalog_http_" + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").cleanDisabled(false).load();
        try {
            flyway.migrate();
            jdbc.execute("ALTER ROLE " + jdbc.queryForObject("SELECT current_user", String.class) + " SET search_path TO " + schema);
            ((com.zaxxer.hikari.HikariDataSource) dataSource).getHikariPoolMXBean().softEvictConnections();
            long id = jdbc.queryForObject("INSERT INTO products (name, sku, price) VALUES (?, ?, 10) RETURNING id", Long.class, "Delete fixture", UUID.randomUUID().toString());
            jdbc.update("UPDATE inventory SET quantity=10 WHERE product_id=?", id);
            var r = request(TargetKind.PRODUCT, id, 3);
            service.reserve(r);
            mvc.perform(delete("/products/" + id)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM products WHERE id=?", Integer.class, id));
            assertEquals(3, jdbc.queryForObject("SELECT reserved_quantity FROM inventory WHERE product_id=?", Integer.class, id));
            assertEquals("RESERVED", service.lookup(r.operationId()).state());
            service.terminal(r.operationId(), false);
            mvc.perform(delete("/products/" + id)).andExpect(status().isOk());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM products WHERE id=?", Integer.class, id));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM inventory WHERE product_id=?", Integer.class, id));
            assertEquals("RELEASED", service.lookup(r.operationId()).state());
            long parent = jdbc.queryForObject("INSERT INTO products (name, sku, price) VALUES (?, ?, 10) RETURNING id", Long.class, "Variant cascade", UUID.randomUUID().toString());
            long child = jdbc.queryForObject("INSERT INTO product_variants (product_id, variant_type, name, value) VALUES (?, 'SIZE', 'Size', 'M') RETURNING id", Long.class, parent);
            jdbc.update("UPDATE variant_inventory SET quantity=10 WHERE variant_id=?", child);
            var variantReservation = request(TargetKind.VARIANT, child, 3);
            service.reserve(variantReservation);
            mvc.perform(delete("/products/" + parent)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM products WHERE id=?", Integer.class, parent));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM variant_inventory WHERE variant_id=?", Integer.class, child));
            assertEquals("RESERVED", service.lookup(variantReservation.operationId()).state());
        } finally {
            jdbc.execute("ALTER ROLE " + jdbc.queryForObject("SELECT current_user", String.class) + " RESET search_path");
            ((com.zaxxer.hikari.HikariDataSource) dataSource).getHikariPoolMXBean().softEvictConnections();
            flyway.clean();
        }
    }

    @Test void variantDeletionPathsPreserveActiveBackingAndHistory() throws Exception {
        String schema = "catalog_variant_" + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").cleanDisabled(false).load();
        var localCaches = new org.springframework.cache.concurrent.ConcurrentMapCacheManager();
        when(cache.getCache(anyString())).thenAnswer(call -> localCaches.getCache(call.getArgument(0)));
        try {
            flyway.migrate();
            jdbc.execute("ALTER ROLE " + jdbc.queryForObject("SELECT current_user", String.class) + " SET search_path TO " + schema);
            ((com.zaxxer.hikari.HikariDataSource) dataSource).getHikariPoolMXBean().softEvictConnections();
            for (int path = 0; path < 3; path++) {
                long p = jdbc.queryForObject("INSERT INTO products (name, sku, price) VALUES (?, ?, 10) RETURNING id", Long.class, "Variant deletion", UUID.randomUUID().toString());
                long sibling = jdbc.queryForObject("INSERT INTO product_variants (product_id, variant_type, name, value) VALUES (?, 'SIZE', 'Size', 'S') RETURNING id", Long.class, p);
                long active = jdbc.queryForObject("INSERT INTO product_variants (product_id, variant_type, name, value) VALUES (?, 'SIZE', 'Size', 'M') RETURNING id", Long.class, p);
                jdbc.update("UPDATE variant_inventory SET quantity=10 WHERE variant_id=?", active);
                var r = request(TargetKind.VARIANT, active, 3);
                service.reserve(r);
                if (path == 0) {
                    mvc.perform(delete("/variants/" + active)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"));
                } else if (path == 1) {
                    assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> productVariantService.deleteVariants(java.util.List.of(sibling, active)));
                } else {
                    assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> variantInventoryService.deleteVariantInventory(active));
                }
                assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM product_variants WHERE product_id=?", Integer.class, p));
                assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM variant_inventory WHERE product_id=?", Integer.class, p));
                assertEquals(3, jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, active));
                assertEquals("RESERVED", service.lookup(r.operationId()).state());
                service.terminal(r.operationId(), true);
                if (path == 0) mvc.perform(delete("/variants/" + active)).andExpect(status().isOk());
                else if (path == 1) productVariantService.deleteVariants(java.util.List.of(sibling, active));
                else variantInventoryService.deleteVariantInventory(active);
                assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM variant_inventory WHERE variant_id=?", Integer.class, active));
                assertEquals("FULFILLED", service.lookup(r.operationId()).state());
            }
        } finally {
            jdbc.execute("ALTER ROLE " + jdbc.queryForObject("SELECT current_user", String.class) + " RESET search_path");
            ((com.zaxxer.hikari.HikariDataSource) dataSource).getHikariPoolMXBean().softEvictConnections();
            flyway.clean();
        }
    }

    @Test void legacyAndDurableReserveRaceBothOrderings() throws Exception {
        String schema = "catalog_legacy_" + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").cleanDisabled(false).load();
        var executor = Executors.newSingleThreadExecutor();
        TransactionTemplate tx = new TransactionTemplate(manager);
        try {
            flyway.migrate();
            for (TargetKind kind : TargetKind.values()) for (boolean fulfill : new boolean[]{false, true}) for (boolean durableFirst : new boolean[]{false, true}) {
                long[] ids = tx.execute(status -> {
                    jdbc.execute("SET LOCAL search_path TO " + schema);
                    long p = jdbc.queryForObject("INSERT INTO products (name, sku, price) VALUES (?, ?, 10) RETURNING id", Long.class, "Legacy race", UUID.randomUUID().toString());
                    long v = jdbc.queryForObject("INSERT INTO product_variants (product_id, variant_type, name, value) VALUES (?, 'SIZE', 'Size', 'M') RETURNING id", Long.class, p);
                    jdbc.update("UPDATE inventory SET quantity=10, reserved_quantity=3 WHERE product_id=?", p);
                    jdbc.update("UPDATE variant_inventory SET quantity=10, reserved_quantity=3 WHERE variant_id=?", v);
                    return new long[]{p, v};
                });
                long id = kind == TargetKind.PRODUCT ? ids[0] : ids[1];
                String table = kind == TargetKind.PRODUCT ? "inventory" : "variant_inventory";
                String key = kind == TargetKind.PRODUCT ? "product_id" : "variant_id";
                var r = request(kind, id, 3);
                CompletableFuture<Integer> pid = new CompletableFuture<>();
                @SuppressWarnings("unchecked") Future<Object>[] pending = new Future[1];
                tx.executeWithoutResult(status -> {
                    jdbc.execute("SET LOCAL search_path TO " + schema);
                    if (durableFirst) service.reserve(r);
                    else assertTrue(legacy(kind, id, fulfill));
                    pending[0] = executor.submit(() -> tx.execute(inner -> {
                        jdbc.execute("SET LOCAL search_path TO " + schema);
                        jdbc.execute("SET LOCAL lock_timeout = '15s'");
                        pid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        return durableFirst ? legacy(kind, id, fulfill) : service.reserve(r);
                    }));
                    try {
                        int worker = pid.get(10, TimeUnit.SECONDS);
                        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(20)).until(() -> {
                            jdbc.execute("SELECT pg_stat_clear_snapshot()");
                            return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock')", Boolean.class, worker));
                        });
                    } catch (Exception ex) { throw new AssertionError(ex); }
                });
                if (durableFirst) {
                    var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> pending[0].get(20, TimeUnit.SECONDS));
                    assertInstanceOf(ReservationException.class, failure.getCause());
                } else assertEquals("RESERVED", ((org.de013.productcatalog.dto.inventory.ReservationResponse) pending[0].get(20, TimeUnit.SECONDS)).state());
                tx.executeWithoutResult(status -> {
                    jdbc.execute("SET LOCAL search_path TO " + schema);
                    var row = jdbc.queryForMap("SELECT quantity,reserved_quantity FROM " + table + " WHERE " + key + "=?", id);
                    assertEquals(!durableFirst && fulfill ? 7 : 10, row.get("quantity"));
                    assertEquals(durableFirst ? 6 : 3, row.get("reserved_quantity"));
                    assertEquals("RESERVED", service.lookup(r.operationId()).state());
                });
            }
        } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS)); flyway.clean(); }
    }

    private boolean legacy(TargetKind kind, long id, boolean fulfill) {
        return kind == TargetKind.PRODUCT ? (fulfill ? inventoryService.fulfillOrder(id, 3) : inventoryService.releaseReservedStock(id, 3))
                : (fulfill ? variantInventoryService.fulfillOrder(id, 3) : variantInventoryService.releaseReservedStock(id, 3));
    }

    @Test void safeAdjustmentsPreserveDurableBacking() {
        for (TargetKind kind : TargetKind.values()) {
            long id = kind == TargetKind.PRODUCT ? product : variant;
            var r = request(kind, id, 3); service.reserve(r);
            if (kind == TargetKind.PRODUCT) {
                inventoryService.addStock(id, 2); inventoryService.removeStock(id, 4); inventoryService.setStock(id, 4);
                assertThrows(org.de013.productcatalog.exception.InsufficientStockException.class, () -> inventoryService.removeStock(id, 2));
                assertThrows(org.de013.productcatalog.exception.InsufficientStockException.class, () -> inventoryService.setStock(id, 2));
            } else {
                variantInventoryService.addStock(id, 2); variantInventoryService.removeStock(id, 4); variantInventoryService.setStock(id, 4);
                assertThrows(org.de013.productcatalog.exception.InsufficientStockException.class, () -> variantInventoryService.removeStock(id, 2));
                assertThrows(org.de013.productcatalog.exception.InsufficientStockException.class, () -> variantInventoryService.setStock(id, 2));
            }
            stock(kind, id, 4, 3);
        }
    }
}
