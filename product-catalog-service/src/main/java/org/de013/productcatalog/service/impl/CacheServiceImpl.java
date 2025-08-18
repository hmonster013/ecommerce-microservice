package org.de013.productcatalog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.productcatalog.entity.Category;
import org.de013.productcatalog.entity.Product;
import org.de013.productcatalog.entity.enums.ProductStatus;
import org.de013.productcatalog.repository.CategoryRepository;
import org.de013.productcatalog.repository.ProductRepository;
import org.de013.productcatalog.repository.SearchAnalyticsRepository;
import org.de013.productcatalog.service.CacheService;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of cache service for centralized cache management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheServiceImpl implements CacheService {

    private final CacheManager cacheManager;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SearchAnalyticsRepository searchAnalyticsRepository;

    @Override
    public void warmupProductCache() {
        log.info("Starting product cache warmup...");
        long startTime = System.currentTimeMillis();
        
        try {
            // Preload featured products
            preloadFeaturedProducts();
            
            // Preload popular products
            preloadPopularProducts(50);
            
            // Preload recently created products
            Page<Product> recentProductsPage = productRepository.findRecentProducts(
                ProductStatus.ACTIVE, PageRequest.of(0, 20));
            Cache productCache = cacheManager.getCache("products");
            if (productCache != null) {
                recentProductsPage.getContent().forEach(product ->
                    productCache.put("product:" + product.getId(), product));
            }
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Product cache warmup completed in {}ms", duration);
            
        } catch (Exception e) {
            log.error("Error during product cache warmup", e);
        }
    }

    @Override
    public void warmupCategoryCache() {
        log.info("Starting category cache warmup...");
        long startTime = System.currentTimeMillis();
        
        try {
            // Preload category hierarchy
            preloadCategoryHierarchy();
            
            // Preload all active categories
            List<Category> categories = categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
            Cache categoryCache = cacheManager.getCache("categories");
            if (categoryCache != null) {
                categories.forEach(category -> {
                    categoryCache.put("category:" + category.getId(), category);
                    categoryCache.put("category:slug:" + category.getSlug(), category);
                });
            }
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Category cache warmup completed in {}ms", duration);
            
        } catch (Exception e) {
            log.error("Error during category cache warmup", e);
        }
    }

    @Override
    public void warmupSearchCache() {
        log.info("Starting search cache warmup...");
        long startTime = System.currentTimeMillis();
        
        try {
            // Preload popular searches
            preloadPopularSearches(20);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Search cache warmup completed in {}ms", duration);
            
        } catch (Exception e) {
            log.error("Error during search cache warmup", e);
        }
    }

    @Override
    public void warmupAllCaches() {
        log.info("Starting complete cache warmup...");
        long startTime = System.currentTimeMillis();
        
        warmupProductCache();
        warmupCategoryCache();
        warmupSearchCache();
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("Complete cache warmup finished in {}ms", duration);
    }

    @Override
    public void evictProductCache(Long productId) {
        log.debug("Evicting product cache for ID: {}", productId);
        
        // Evict from multiple product-related caches
        evictFromCache("products", "product:" + productId);
        evictFromCache("productDetails", "product:" + productId);
        evictFromCache("similarProducts", "product:" + productId);
        
        // Evict related caches that might contain this product
        evictAllFromCache("featuredProducts");
        evictAllFromCache("popularProducts");
        evictAllFromCache("trendingProducts");
        evictAllFromCache("searchResults");
    }

    @Override
    public void evictCategoryCache(Long categoryId) {
        log.debug("Evicting category cache for ID: {}", categoryId);
        
        evictFromCache("categories", "category:" + categoryId);
        evictAllFromCache("categoryTree");
        evictAllFromCache("categoryHierarchy");
        evictAllFromCache("productsByCategory");
    }

    @Override
    public void evictSearchCache(String query) {
        log.debug("Evicting search cache for query: {}", query);
        
        evictFromCache("searchResults", "search:" + query);
        evictFromCache("searchSuggestions", "suggestions:" + query);
    }

    @Override
    public void evictAllProductCaches() {
        log.info("Evicting all product caches");
        
        String[] productCaches = {
            "products", "productDetails", "productsByCategory", 
            "featuredProducts", "popularProducts", "similarProducts", "trendingProducts"
        };
        
        for (String cacheName : productCaches) {
            evictAllFromCache(cacheName);
        }
    }

    @Override
    public void evictAllCategoryCaches() {
        log.info("Evicting all category caches");
        
        String[] categoryCaches = {
            "categories", "categoryTree", "categoryHierarchy", "categoryBySlug"
        };
        
        for (String cacheName : categoryCaches) {
            evictAllFromCache(cacheName);
        }
    }

    @Override
    public void evictAllSearchCaches() {
        log.info("Evicting all search caches");
        
        String[] searchCaches = {
            "searchResults", "searchSuggestions", "popularSearches", "autocompleteSuggestions"
        };
        
        for (String cacheName : searchCaches) {
            evictAllFromCache(cacheName);
        }
    }

    @Override
    public void evictAllCaches() {
        log.info("Evicting all caches");
        
        cacheManager.getCacheNames().forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    @Override
    public CacheStatsDto getCacheStatistics() {
        // This is a simplified implementation
        // In a real scenario, you'd integrate with cache metrics
        return new CacheStatsDto("overall", 0, 0, 0, 0.0, 0, 0.0, 0);
    }

    @Override
    public CacheStatsDto getCacheStatistics(String cacheName) {
        // This is a simplified implementation
        return new CacheStatsDto(cacheName, 0, 0, 0, 0.0, 0, 0.0, 0);
    }

    @Override
    public List<String> getCacheNames() {
        return new ArrayList<>(cacheManager.getCacheNames());
    }

    @Override
    public long getCacheSize(String cacheName) {
        // This would require integration with specific cache implementation
        return 0;
    }

    @Override
    public double getCacheHitRate(String cacheName) {
        // This would require integration with cache metrics
        return 0.0;
    }

    @Override
    public boolean isCacheHealthy() {
        try {
            // Simple health check - try to access Redis
            redisTemplate.hasKey("health-check");
            return true;
        } catch (Exception e) {
            log.error("Cache health check failed", e);
            return false;
        }
    }

    @Override
    public CacheHealthDto getCacheHealth() {
        boolean healthy = isCacheHealthy();
        List<String> cacheNames = getCacheNames();
        
        return new CacheHealthDto(
            healthy,
            healthy ? "HEALTHY" : "UNHEALTHY",
            cacheNames.size(),
            healthy ? cacheNames.size() : 0,
            healthy ? 0 : cacheNames.size(),
            0.0, // Would calculate from actual metrics
            0L,  // Would calculate from actual metrics
            healthy ? Collections.emptyList() : List.of("Redis connection failed"),
            healthy ? Collections.emptyList() : List.of("Check Redis server status")
        );
    }

    @Override
    public void validateCacheConfiguration() {
        log.info("Validating cache configuration...");
        
        List<String> expectedCaches = Arrays.asList(
            "products", "categories", "searchResults", "inventory"
        );
        
        List<String> actualCaches = getCacheNames();
        
        for (String expectedCache : expectedCaches) {
            if (!actualCaches.contains(expectedCache)) {
                log.warn("Expected cache '{}' not found in configuration", expectedCache);
            }
        }
        
        log.info("Cache configuration validation completed");
    }

    @Override
    public Set<String> getCacheKeys(String cacheName) {
        // This would require specific Redis operations
        return Collections.emptySet();
    }

    @Override
    public boolean existsInCache(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        return cache != null && cache.get(key) != null;
    }

    @Override
    public void removeFromCache(String cacheName, String key) {
        evictFromCache(cacheName, key);
    }

    @Override
    public Object getFromCache(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(key);
            return wrapper != null ? wrapper.get() : null;
        }
        return null;
    }

    @Override
    public void putInCache(String cacheName, String key, Object value) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.put(key, value);
        }
    }

    @Override
    public void preloadPopularProducts(int limit) {
        log.debug("Preloading {} popular products", limit);
        
        Page<Product> popularProductsPage = productRepository.findByIsFeaturedTrue(
            PageRequest.of(0, limit));
        List<Product> popularProducts = popularProductsPage.getContent().stream()
            .filter(product -> product.getStatus() == ProductStatus.ACTIVE)
            .collect(Collectors.toList());
        
        Cache cache = cacheManager.getCache("popularProducts");
        if (cache != null) {
            cache.put("popular:all", popularProducts);
        }
    }

    @Override
    public void preloadFeaturedProducts() {
        log.debug("Preloading featured products");
        
        List<Product> featuredProducts = productRepository.findByIsFeaturedTrue().stream()
            .filter(product -> product.getStatus() == ProductStatus.ACTIVE)
            .collect(Collectors.toList());
        
        Cache cache = cacheManager.getCache("featuredProducts");
        if (cache != null) {
            cache.put("featured:all", featuredProducts);
        }
    }

    @Override
    public void preloadCategoryHierarchy() {
        log.debug("Preloading category hierarchy");
        
        List<Category> rootCategories = categoryRepository.findByParentIsNullAndIsActiveTrueOrderByDisplayOrderAsc();
        
        Cache cache = cacheManager.getCache("categoryTree");
        if (cache != null) {
            cache.put("tree:root", rootCategories);
        }
    }

    @Override
    public void preloadPopularSearches(int limit) {
        log.debug("Preloading {} popular searches", limit);
        
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<Object[]> popularQueries = searchAnalyticsRepository.findPopularQueriesWithResults(
            since, PageRequest.of(0, limit));
        
        List<String> queries = popularQueries.stream()
            .map(row -> (String) row[0])
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        Cache cache = cacheManager.getCache("popularSearches");
        if (cache != null) {
            cache.put("popular:all", queries);
        }
    }

    @Override
    public void cleanupExpiredEntries() {
        log.info("Cleaning up expired cache entries");
        // This would be handled automatically by Redis TTL
    }

    @Override
    public void optimizeCacheMemory() {
        log.info("Optimizing cache memory usage");
        // This could involve compacting Redis memory or adjusting cache sizes
    }

    @Override
    public void rebuildCacheIndexes() {
        log.info("Rebuilding cache indexes");
        // This would involve rebuilding any secondary indexes in Redis
    }

    // Helper methods
    
    private void evictFromCache(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }
    
    private void evictAllFromCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
