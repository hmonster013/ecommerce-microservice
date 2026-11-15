package org.de013.productcatalog.integration;

import jakarta.persistence.EntityManager;
import org.de013.productcatalog.dto.inventory.VariantInventoryUpdateDto;
import org.de013.productcatalog.entity.Inventory;
import org.de013.productcatalog.entity.Product;
import org.de013.productcatalog.entity.ProductVariant;
import org.de013.productcatalog.entity.VariantInventory;
import org.de013.productcatalog.entity.enums.VariantType;
import org.de013.productcatalog.exception.InsufficientStockException;
import org.de013.productcatalog.exception.InvalidStockQuantityException;
import org.de013.common.exception.ResourceNotFoundException;
import org.de013.productcatalog.repository.InventoryRepository;
import org.de013.productcatalog.repository.VariantInventoryRepository;
import org.de013.productcatalog.service.InventoryService;
import org.de013.productcatalog.service.VariantInventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import javax.sql.DataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Exercises real PostgreSQL row locks, not H2 or mocked repositories. */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=WARN",
        "logging.level.org.de013.productcatalog=WARN"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InventoryConcurrencyTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired private InventoryService inventoryService;
    @Autowired private VariantInventoryService variantService;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private VariantInventoryRepository variantRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockBean(name = "cacheManager") private CacheManager cacheManager;

    private TransactionTemplate transaction;
    private ExecutorService workers;
    private Long productId;
    private Long variantId;

    @BeforeEach
    void createIsolatedInventory() {
        jdbc.execute("DROP TABLE IF EXISTS inventory_reservations");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V11__Create_inventory_reservations.sql")).execute(dataSource);
        CacheManager localCaches = new TransactionAwareCacheManagerProxy(new ConcurrentMapCacheManager());
        when(cacheManager.getCache(anyString())).thenAnswer(call -> localCaches.getCache(call.getArgument(0)));
        transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(20);
        workers = Executors.newFixedThreadPool(2);
        transaction.executeWithoutResult(status -> {
            Product product = Product.builder().name("Concurrency fixture")
                    .sku("LOCK-" + UUID.randomUUID()).price(new BigDecimal("10.00")).build();
            entityManager.persist(product);
            ProductVariant variant = ProductVariant.builder().product(product).variantType(VariantType.SIZE)
                    .name("Size").value("M").build();
            entityManager.persist(variant);
            entityManager.persist(Inventory.builder().product(product).quantity(10).build());
            entityManager.persist(VariantInventory.builder().product(product).variant(variant).quantity(10).build());
            entityManager.flush();
            productId = product.getId();
            variantId = variant.getId();
        });
    }

    @AfterEach
    void stopWorkers() throws InterruptedException {
        workers.shutdownNow();
        assertTrue(workers.awaitTermination(25, TimeUnit.SECONDS), "Inventory workers must finish");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentAddsWaitForRowLockAndDoNotLoseAnIncrement(boolean variant) throws Exception {
        Future<?>[] pending = new Future<?>[2];
        transaction.executeWithoutResult(status -> {
            lockRow(variant);
            pending[0] = startBlockedWorker(() -> { add(variant, 3); return null; });
            pending[1] = startBlockedWorker(() -> { add(variant, 7); return null; });
        });
        pending[0].get(20, TimeUnit.SECONDS);
        pending[1].get(20, TimeUnit.SECONDS);
        assertStock(variant, 20, 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void setCannotOverwriteAReservationCommittedWhileWaiting(boolean variant) throws Exception {
        Future<?> pending = reserveThenStart(variant, () -> { set(variant, 2); return null; });
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> pending.get(20, TimeUnit.SECONDS));
        assertInstanceOf(InsufficientStockException.class, failure.getCause());
        assertStock(variant, 10, 8);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void removeCannotConsumeAReservationCommittedWhileWaiting(boolean variant) throws Exception {
        Future<?> pending = reserveThenStart(variant, () -> { remove(variant, 3); return null; });
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> pending.get(20, TimeUnit.SECONDS));
        assertInstanceOf(InsufficientStockException.class, failure.getCause());
        assertStock(variant, 10, 8);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reservationWaitsForAdjustmentAndUsesCommittedQuantity(boolean variant) throws Exception {
        Future<?>[] pending = new Future<?>[1];
        transaction.executeWithoutResult(status -> {
            set(variant, 2);
            pending[0] = startBlockedWorker(() -> reserve(variant, 8));
        });
        assertEquals(false, pending[0].get(20, TimeUnit.SECONDS));
        assertStock(variant, 2, 0);
    }

    @Test
    void variantLocationUpdateDoesNotOverwriteReservation() throws Exception {
        Future<?> pending = reserveThenStart(true, () -> {
            variantService.updateVariantLocation(variantId, "Warehouse B");
            return null;
        });
        pending.get(20, TimeUnit.SECONDS);
        assertStock(true, 10, 8);
        assertEquals("Warehouse B", variantService.getVariantInventoryByVariantId(variantId).getLocation());
    }

    @Test
    void variantConfigurationUpdateDoesNotOverwriteReservation() throws Exception {
        Future<?> pending = reserveThenStart(true, () -> variantService.updateVariantInventory(variantId,
                new VariantInventoryUpdateDto(2, null, null, null, null, null, "Warehouse C", null)));
        pending.get(20, TimeUnit.SECONDS);
        assertStock(true, 10, 8);
        assertEquals(2, variantService.getVariantInventoryByVariantId(variantId).getMinStockLevel());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void releaseWaitsForAdjustmentWithoutLosingEitherChange(boolean variant) throws Exception {
        assertTrue(reserve(variant, 6));
        Future<?>[] pending = new Future<?>[1];
        transaction.executeWithoutResult(status -> {
            add(variant, 2);
            pending[0] = startBlockedWorker(() -> release(variant, 3));
        });
        assertEquals(true, pending[0].get(20, TimeUnit.SECONDS));
        assertStock(variant, 12, 3);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fulfillWaitsForAdjustmentWithoutLosingEitherChange(boolean variant) throws Exception {
        assertTrue(reserve(variant, 6));
        Future<?>[] pending = new Future<?>[1];
        transaction.executeWithoutResult(status -> {
            add(variant, 2);
            pending[0] = startBlockedWorker(() -> fulfill(variant, 3));
        });
        assertEquals(true, pending[0].get(20, TimeUnit.SECONDS));
        assertStock(variant, 9, 3);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rollbackReleasesLockAndDoesNotPersistAdjustment(boolean variant) throws Exception {
        transaction.executeWithoutResult(status -> {
            add(variant, 5);
            status.setRollbackOnly();
        });
        workers.submit(() -> add(variant, 2)).get(20, TimeUnit.SECONDS);
        assertStock(variant, 12, 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invalidQuantitiesAndOverflowLeaveStockUnchanged(boolean variant) {
        for (Integer invalid : new Integer[]{null, 0, -1}) {
            assertThrows(InvalidStockQuantityException.class, () -> add(variant, invalid));
            assertThrows(InvalidStockQuantityException.class, () -> remove(variant, invalid));
            assertThrows(InvalidStockQuantityException.class, () -> reserve(variant, invalid));
            assertThrows(InvalidStockQuantityException.class, () -> release(variant, invalid));
            assertThrows(InvalidStockQuantityException.class, () -> fulfill(variant, invalid));
        }
        assertThrows(InvalidStockQuantityException.class, () -> set(variant, null));
        assertThrows(InvalidStockQuantityException.class, () -> set(variant, -1));
        assertThrows(InvalidStockQuantityException.class, () -> add(variant, Integer.MAX_VALUE));
        assertStock(variant, 10, 0);
        set(variant, 0);
        assertStock(variant, 0, 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reserveReleaseAndFulfillRetainBooleanContract(boolean variant) {
        assertTrue(reserve(variant, 8));
        assertFalse(reserve(variant, 3));
        assertTrue(release(variant, 2));
        assertTrue(fulfill(variant, 4));
        assertFalse(release(variant, 3));
        assertFalse(fulfill(variant, 3));
        assertStock(variant, 6, 2);
    }

    @Test
    void missingAdjustmentResourceIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> inventoryService.addStock(Long.MAX_VALUE, 1));
        assertThrows(ResourceNotFoundException.class, () -> variantService.addStock(Long.MAX_VALUE, 1));
    }

    private Future<?> reserveThenStart(boolean variant, Callable<?> action) {
        Future<?>[] pending = new Future<?>[1];
        transaction.executeWithoutResult(status -> {
            assertTrue(reserve(variant, 8));
            pending[0] = startBlockedWorker(action);
        });
        return pending[0];
    }

    private Future<?> startBlockedWorker(Callable<?> action) {
        CompletableFuture<Integer> backendPid = new CompletableFuture<>();
        Future<?> result = workers.submit(() -> transaction.execute(status -> {
            jdbc.execute("SET LOCAL lock_timeout = '15s'");
            backendPid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
            try {
                return action.call();
            } catch (RuntimeException failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }));
        try {
            int pid = backendPid.get(10, TimeUnit.SECONDS);
            // Prove the request reached a PostgreSQL lock wait, rather than relying on thread timing.
            await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(20)).until(() -> {
                jdbc.execute("SELECT pg_stat_clear_snapshot()");
                return Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE pid = ? AND wait_event_type = 'Lock')",
                        Boolean.class, pid));
            });
            assertFalse(result.isDone());
            return result;
        } catch (Exception failure) {
            throw new AssertionError("Worker did not reach a PostgreSQL row lock", failure);
        }
    }

    private void lockRow(boolean variant) {
        if (variant) variantRepository.findByVariantIdForUpdate(variantId).orElseThrow();
        else inventoryRepository.findByProductIdForUpdate(productId).orElseThrow();
    }

    private void add(boolean variant, Integer quantity) {
        if (variant) variantService.addStock(variantId, quantity);
        else inventoryService.addStock(productId, quantity);
    }

    private void remove(boolean variant, Integer quantity) {
        if (variant) variantService.removeStock(variantId, quantity);
        else inventoryService.removeStock(productId, quantity);
    }

    private void set(boolean variant, Integer quantity) {
        if (variant) variantService.setStock(variantId, quantity);
        else inventoryService.setStock(productId, quantity);
    }

    private boolean reserve(boolean variant, Integer quantity) {
        return variant ? variantService.reserveStock(variantId, quantity) : inventoryService.reserveStock(productId, quantity);
    }

    private boolean release(boolean variant, Integer quantity) {
        return variant ? variantService.releaseReservedStock(variantId, quantity) : inventoryService.releaseReservedStock(productId, quantity);
    }

    private boolean fulfill(boolean variant, Integer quantity) {
        return variant ? variantService.fulfillOrder(variantId, quantity) : inventoryService.fulfillOrder(productId, quantity);
    }

    private void assertStock(boolean variant, int quantity, int reserved) {
        transaction.executeWithoutResult(status -> {
            if (variant) {
                VariantInventory stock = variantRepository.findByVariantId(variantId).orElseThrow();
                assertEquals(quantity, stock.getQuantity());
                assertEquals(reserved, stock.getReservedQuantity());
            } else {
                Inventory stock = inventoryRepository.findByProductId(productId).orElseThrow();
                assertEquals(quantity, stock.getQuantity());
                assertEquals(reserved, stock.getReservedQuantity());
            }
        });
    }
}
