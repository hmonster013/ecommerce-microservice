package org.de013.productcatalog.service.support;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class InventoryCacheInvalidator {
    private final CacheManager cacheManager;

    public void productChanged(Long productId) {
        afterCommit(() -> {
            evict("inventory", productId);
            evict("products", productId);
        });
    }

    public void variantChanged(Long variantId) {
        afterCommit(() -> evict("variantInventory", variantId));
    }

    private void afterCommit(Runnable eviction) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eviction.run();
                }
            });
        } else {
            eviction.run();
        }
    }

    private void evict(String name, Long id) {
        Cache cache = cacheManager.getCache(name);
        if (cache != null) {
            // Immediate eviction: a transaction-aware cache must not defer again inside afterCommit.
            cache.evictIfPresent(id);
        }
    }
}
