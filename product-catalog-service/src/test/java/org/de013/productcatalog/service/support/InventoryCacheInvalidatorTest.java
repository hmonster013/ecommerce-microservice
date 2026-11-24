package org.de013.productcatalog.service.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;

class InventoryCacheInvalidatorTest {
    private final ConcurrentMapCacheManager caches = new ConcurrentMapCacheManager("inventory", "products", "variantInventory");
    private final InventoryCacheInvalidator invalidator = new InventoryCacheInvalidator(caches);

    @AfterEach
    void clearSynchronization() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void productCachesAreEvictedOnlyAfterCommit() {
        caches.getCache("inventory").put(1L, "old");
        caches.getCache("products").put(1L, "old");
        beginTransaction();
        invalidator.productChanged(1L);
        assertNotNull(caches.getCache("inventory").get(1L));
        assertNotNull(caches.getCache("products").get(1L));
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertNull(caches.getCache("inventory").get(1L));
        assertNull(caches.getCache("products").get(1L));
    }

    @Test
    void rollbackDoesNotEvictVariantCache() {
        caches.getCache("variantInventory").put(2L, "old");
        beginTransaction();
        invalidator.variantChanged(2L);
        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertNotNull(caches.getCache("variantInventory").get(2L));
    }

    @Test
    void variantCacheIsEvictedAfterCommitWithoutAnAopSelfInvocation() {
        caches.getCache("variantInventory").put(2L, "old");
        beginTransaction();
        invalidator.variantChanged(2L);
        assertNotNull(caches.getCache("variantInventory").get(2L));
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertNull(caches.getCache("variantInventory").get(2L));
    }

    @Test
    void explicitInvalidationOutsideATransactionIsImmediate() {
        caches.getCache("variantInventory").put(2L, "old");
        invalidator.variantChanged(2L);
        assertNull(caches.getCache("variantInventory").get(2L));
    }

    @Test
    void transactionAwareCacheDoesNotDeferEvictionTwice() {
        caches.getCache("inventory").put(1L, "old");
        InventoryCacheInvalidator transactionAware = new InventoryCacheInvalidator(
                new TransactionAwareCacheManagerProxy(caches));
        beginTransaction();
        transactionAware.productChanged(1L);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertNull(caches.getCache("inventory").get(1L));
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }
}
