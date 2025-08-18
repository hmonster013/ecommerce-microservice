package org.de013.productcatalog.service;

import java.util.List;
import java.util.Set;

/**
 * Service for centralized cache management and operations.
 */
public interface CacheService {

    // Cache warming operations
    void warmupProductCache();
    void warmupCategoryCache();
    void warmupSearchCache();
    void warmupAllCaches();

    // Cache invalidation operations
    void evictProductCache(Long productId);
    void evictCategoryCache(Long categoryId);
    void evictSearchCache(String query);
    void evictAllProductCaches();
    void evictAllCategoryCaches();
    void evictAllSearchCaches();
    void evictAllCaches();

    // Cache statistics and monitoring
    CacheStatsDto getCacheStatistics();
    CacheStatsDto getCacheStatistics(String cacheName);
    List<String> getCacheNames();
    long getCacheSize(String cacheName);
    double getCacheHitRate(String cacheName);

    // Cache health and diagnostics
    boolean isCacheHealthy();
    CacheHealthDto getCacheHealth();
    void validateCacheConfiguration();

    // Cache key management
    Set<String> getCacheKeys(String cacheName);
    boolean existsInCache(String cacheName, String key);
    void removeFromCache(String cacheName, String key);
    Object getFromCache(String cacheName, String key);
    void putInCache(String cacheName, String key, Object value);

    // Bulk cache operations
    void preloadPopularProducts(int limit);
    void preloadFeaturedProducts();
    void preloadCategoryHierarchy();
    void preloadPopularSearches(int limit);

    // Cache maintenance
    void cleanupExpiredEntries();
    void optimizeCacheMemory();
    void rebuildCacheIndexes();

    /**
     * DTO for cache statistics
     */
    class CacheStatsDto {
        private String cacheName;
        private long size;
        private long hitCount;
        private long missCount;
        private double hitRate;
        private long evictionCount;
        private double averageLoadTime;
        private long totalLoadTime;

        // Constructors, getters, setters
        public CacheStatsDto() {}

        public CacheStatsDto(String cacheName, long size, long hitCount, long missCount, 
                           double hitRate, long evictionCount, double averageLoadTime, long totalLoadTime) {
            this.cacheName = cacheName;
            this.size = size;
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.hitRate = hitRate;
            this.evictionCount = evictionCount;
            this.averageLoadTime = averageLoadTime;
            this.totalLoadTime = totalLoadTime;
        }

        // Getters and setters
        public String getCacheName() { return cacheName; }
        public void setCacheName(String cacheName) { this.cacheName = cacheName; }
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
        public long getHitCount() { return hitCount; }
        public void setHitCount(long hitCount) { this.hitCount = hitCount; }
        public long getMissCount() { return missCount; }
        public void setMissCount(long missCount) { this.missCount = missCount; }
        public double getHitRate() { return hitRate; }
        public void setHitRate(double hitRate) { this.hitRate = hitRate; }
        public long getEvictionCount() { return evictionCount; }
        public void setEvictionCount(long evictionCount) { this.evictionCount = evictionCount; }
        public double getAverageLoadTime() { return averageLoadTime; }
        public void setAverageLoadTime(double averageLoadTime) { this.averageLoadTime = averageLoadTime; }
        public long getTotalLoadTime() { return totalLoadTime; }
        public void setTotalLoadTime(long totalLoadTime) { this.totalLoadTime = totalLoadTime; }
    }

    /**
     * DTO for cache health status
     */
    class CacheHealthDto {
        private boolean healthy;
        private String status;
        private int totalCaches;
        private int healthyCaches;
        private int unhealthyCaches;
        private double overallHitRate;
        private long totalMemoryUsage;
        private List<String> issues;
        private List<String> recommendations;

        // Constructors, getters, setters
        public CacheHealthDto() {}

        public CacheHealthDto(boolean healthy, String status, int totalCaches, int healthyCaches, 
                            int unhealthyCaches, double overallHitRate, long totalMemoryUsage,
                            List<String> issues, List<String> recommendations) {
            this.healthy = healthy;
            this.status = status;
            this.totalCaches = totalCaches;
            this.healthyCaches = healthyCaches;
            this.unhealthyCaches = unhealthyCaches;
            this.overallHitRate = overallHitRate;
            this.totalMemoryUsage = totalMemoryUsage;
            this.issues = issues;
            this.recommendations = recommendations;
        }

        // Getters and setters
        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getTotalCaches() { return totalCaches; }
        public void setTotalCaches(int totalCaches) { this.totalCaches = totalCaches; }
        public int getHealthyCaches() { return healthyCaches; }
        public void setHealthyCaches(int healthyCaches) { this.healthyCaches = healthyCaches; }
        public int getUnhealthyCaches() { return unhealthyCaches; }
        public void setUnhealthyCaches(int unhealthyCaches) { this.unhealthyCaches = unhealthyCaches; }
        public double getOverallHitRate() { return overallHitRate; }
        public void setOverallHitRate(double overallHitRate) { this.overallHitRate = overallHitRate; }
        public long getTotalMemoryUsage() { return totalMemoryUsage; }
        public void setTotalMemoryUsage(long totalMemoryUsage) { this.totalMemoryUsage = totalMemoryUsage; }
        public List<String> getIssues() { return issues; }
        public void setIssues(List<String> issues) { this.issues = issues; }
        public List<String> getRecommendations() { return recommendations; }
        public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }
    }
}
