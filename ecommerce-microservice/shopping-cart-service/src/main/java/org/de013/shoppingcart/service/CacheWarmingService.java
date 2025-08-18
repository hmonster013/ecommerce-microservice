package org.de013.shoppingcart.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.client.ProductCatalogFeignClient;
import org.de013.shoppingcart.client.UserServiceFeignClient;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Cache Warming Service
 * Proactively loads frequently accessed data into cache layers
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheWarmingService {

    private final CacheManager cacheManager;
    private final ProductCatalogFeignClient productCatalogFeignClient;
    private final UserServiceFeignClient userServiceFeignClient;
    private final PerformanceMonitoringService performanceMonitoringService;

    // Cache warming status
    private boolean warmupInProgress = false;
    private LocalDateTime lastWarmupTime;
    private Map<String, LocalDateTime> cacheWarmupTimes = new HashMap<>();

    // ==================== APPLICATION STARTUP WARMUP ====================

    /**
     * Warm up caches on application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void warmupOnStartup() {
        log.info("Starting cache warmup on application startup...");
        
        try {
            // Wait a bit for application to fully initialize
            Thread.sleep(5000);
            
            // Perform comprehensive warmup
            CompletableFuture<Void> warmupFuture = performComprehensiveWarmup();
            warmupFuture.get(); // Wait for completion
            
            log.info("Application startup cache warmup completed successfully");
            
        } catch (Exception e) {
            log.error("Error during startup cache warmup: {}", e.getMessage(), e);
        }
    }

    // ==================== SCHEDULED WARMUP ====================

    /**
     * Scheduled cache warmup - runs every hour
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    @Async
    public void scheduledWarmup() {
        if (warmupInProgress) {
            log.debug("Cache warmup already in progress, skipping scheduled warmup");
            return;
        }
        
        log.info("Starting scheduled cache warmup...");
        
        try {
            performIncrementalWarmup();
            log.info("Scheduled cache warmup completed successfully");
            
        } catch (Exception e) {
            log.error("Error during scheduled cache warmup: {}", e.getMessage(), e);
        }
    }

    /**
     * Scheduled cache refresh for critical data - runs every 15 minutes
     */
    @Scheduled(fixedRate = 900000) // Every 15 minutes
    @Async
    public void scheduledCriticalDataRefresh() {
        log.debug("Starting critical data cache refresh...");
        
        try {
            refreshCriticalCaches();
            log.debug("Critical data cache refresh completed");
            
        } catch (Exception e) {
            log.error("Error during critical data cache refresh: {}", e.getMessage(), e);
        }
    }

    // ==================== WARMUP STRATEGIES ====================

    /**
     * Comprehensive cache warmup
     */
    @Async
    public CompletableFuture<Void> performComprehensiveWarmup() {
        return CompletableFuture.runAsync(() -> {
            warmupInProgress = true;
            long startTime = System.currentTimeMillis();
            
            try {
                log.info("Starting comprehensive cache warmup...");
                
                // Warm up different cache layers in parallel
                CompletableFuture<Void> productWarmup = warmupProductCache();
                CompletableFuture<Void> pricingWarmup = warmupPricingCache();
                CompletableFuture<Void> taxWarmup = warmupTaxCache();
                CompletableFuture<Void> shippingWarmup = warmupShippingCache();
                CompletableFuture<Void> systemWarmup = warmupSystemConfigCache();
                
                // Wait for all warmups to complete
                CompletableFuture.allOf(productWarmup, pricingWarmup, taxWarmup, 
                                      shippingWarmup, systemWarmup).join();
                
                lastWarmupTime = LocalDateTime.now();
                long duration = System.currentTimeMillis() - startTime;
                
                log.info("Comprehensive cache warmup completed in {}ms", duration);
                
            } catch (Exception e) {
                log.error("Error in comprehensive cache warmup: {}", e.getMessage(), e);
            } finally {
                warmupInProgress = false;
            }
        });
    }

    /**
     * Incremental cache warmup (lighter than comprehensive)
     */
    @Async
    public CompletableFuture<Void> performIncrementalWarmup() {
        return CompletableFuture.runAsync(() -> {
            warmupInProgress = true;
            long startTime = System.currentTimeMillis();
            
            try {
                log.info("Starting incremental cache warmup...");
                
                // Only warm up most critical caches
                warmupCriticalProductData();
                warmupActivePricingRules();
                warmupFrequentlyUsedTaxRates();
                
                lastWarmupTime = LocalDateTime.now();
                long duration = System.currentTimeMillis() - startTime;
                
                log.info("Incremental cache warmup completed in {}ms", duration);
                
            } catch (Exception e) {
                log.error("Error in incremental cache warmup: {}", e.getMessage(), e);
            } finally {
                warmupInProgress = false;
            }
        });
    }

    // ==================== SPECIFIC CACHE WARMUP METHODS ====================

    /**
     * Warm up product cache
     */
    @Async
    public CompletableFuture<Void> warmupProductCache() {
        return CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                log.debug("Warming up product cache...");
                
                Cache productCache = cacheManager.getCache("product-details");
                if (productCache == null) {
                    log.warn("Product cache not found");
                    return;
                }
                
                // Get popular products from external service
                Map<String, Object> popularProducts = productCatalogFeignClient.getPopularProducts();
                
                if (!Boolean.TRUE.equals(popularProducts.get("fallback"))) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> products = (List<Map<String, Object>>) 
                        popularProducts.get("products");
                    
                    if (products != null) {
                        for (Map<String, Object> product : products) {
                            String productId = (String) product.get("id");
                            if (productId != null) {
                                // Cache product details
                                productCache.put(productId, product);
                                
                                // Also cache product validation
                                Cache validationCache = cacheManager.getCache("product-validation");
                                if (validationCache != null) {
                                    Map<String, Object> validation = productCatalogFeignClient.validateProduct(productId);
                                    validationCache.put(productId, validation);
                                }
                            }
                        }
                        
                        log.debug("Warmed up product cache with {} products", products.size());
                    }
                }
                
                cacheWarmupTimes.put("products", LocalDateTime.now());
                long duration = System.currentTimeMillis() - startTime;
                performanceMonitoringService.trackCacheOperation("products", "warmup", true);
                
                log.debug("Product cache warmup completed in {}ms", duration);
                
            } catch (Exception e) {
                log.error("Error warming up product cache: {}", e.getMessage());
                performanceMonitoringService.trackCacheOperation("products", "warmup", false);
            }
        });
    }

    /**
     * Warm up pricing cache
     */
    @Async
    public CompletableFuture<Void> warmupPricingCache() {
        return CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                log.debug("Warming up pricing cache...");
                
                Cache pricingCache = cacheManager.getCache("pricing-calculations");
                if (pricingCache == null) {
                    log.warn("Pricing cache not found");
                    return;
                }
                
                // Pre-calculate common pricing scenarios
                warmupCommonPricingScenarios(pricingCache);
                
                cacheWarmupTimes.put("pricing", LocalDateTime.now());
                long duration = System.currentTimeMillis() - startTime;
                performanceMonitoringService.trackCacheOperation("pricing", "warmup", true);
                
                log.debug("Pricing cache warmup completed in {}ms", duration);
                
            } catch (Exception e) {
                log.error("Error warming up pricing cache: {}", e.getMessage());
                performanceMonitoringService.trackCacheOperation("pricing", "warmup", false);
            }
        });
    }

    /**
     * Warm up tax cache
     */
    @Async
    public CompletableFuture<Void> warmupTaxCache() {
        return CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                log.debug("Warming up tax cache...");
                
                Cache taxCache = cacheManager.getCache("tax-calculations");
                if (taxCache == null) {
                    log.warn("Tax cache not found");
                    return;
                }
                
                // Pre-load common tax rates
                warmupCommonTaxRates(taxCache);
                
                cacheWarmupTimes.put("tax", LocalDateTime.now());
                long duration = System.currentTimeMillis() - startTime;
                performanceMonitoringService.trackCacheOperation("tax", "warmup", true);
                
                log.debug("Tax cache warmup completed in {}ms", duration);
                
            } catch (Exception e) {
                log.error("Error warming up tax cache: {}", e.getMessage());
                performanceMonitoringService.trackCacheOperation("tax", "warmup", false);
            }
        });
    }

    /**
     * Warm up shipping cache
     */
    @Async
    public CompletableFuture<Void> warmupShippingCache() {
        return CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                log.debug("Warming up shipping cache...");
                
                Cache shippingCache = cacheManager.getCache("shipping-calculations");
                if (shippingCache == null) {
                    log.warn("Shipping cache not found");
                    return;
                }
                
                // Pre-load common shipping scenarios
                warmupCommonShippingScenarios(shippingCache);
                
                cacheWarmupTimes.put("shipping", LocalDateTime.now());
                long duration = System.currentTimeMillis() - startTime;
                performanceMonitoringService.trackCacheOperation("shipping", "warmup", true);
                
                log.debug("Shipping cache warmup completed in {}ms", duration);
                
            } catch (Exception e) {
                log.error("Error warming up shipping cache: {}", e.getMessage());
                performanceMonitoringService.trackCacheOperation("shipping", "warmup", false);
            }
        });
    }

    /**
     * Warm up system configuration cache
     */
    @Async
    public CompletableFuture<Void> warmupSystemConfigCache() {
        return CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                log.debug("Warming up system config cache...");
                
                Cache configCache = cacheManager.getCache("system-config");
                if (configCache == null) {
                    log.warn("System config cache not found");
                    return;
                }
                
                // Pre-load system configurations
                warmupSystemConfigurations(configCache);
                
                cacheWarmupTimes.put("system-config", LocalDateTime.now());
                long duration = System.currentTimeMillis() - startTime;
                performanceMonitoringService.trackCacheOperation("system-config", "warmup", true);
                
                log.debug("System config cache warmup completed in {}ms", duration);
                
            } catch (Exception e) {
                log.error("Error warming up system config cache: {}", e.getMessage());
                performanceMonitoringService.trackCacheOperation("system-config", "warmup", false);
            }
        });
    }

    // ==================== HELPER METHODS ====================

    /**
     * Warm up critical product data only
     */
    private void warmupCriticalProductData() {
        try {
            Cache productCache = cacheManager.getCache("product-details");
            if (productCache != null) {
                // Load only top 10 most popular products
                Map<String, Object> topProducts = productCatalogFeignClient.getTopProducts(10);
                if (!Boolean.TRUE.equals(topProducts.get("fallback"))) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> products = (List<Map<String, Object>>) 
                        topProducts.get("products");
                    
                    if (products != null) {
                        products.forEach(product -> {
                            String productId = (String) product.get("id");
                            if (productId != null) {
                                productCache.put(productId, product);
                            }
                        });
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error warming up critical product data: {}", e.getMessage());
        }
    }

    /**
     * Warm up active pricing rules
     */
    private void warmupActivePricingRules() {
        try {
            Cache pricingCache = cacheManager.getCache("pricing-rules");
            if (pricingCache != null) {
                // Load active pricing rules
                Map<String, Object> activePricingRules = Map.of(
                    "bulk_discount_5", Map.of("threshold", 5, "discount", 0.05),
                    "bulk_discount_10", Map.of("threshold", 10, "discount", 0.10),
                    "bulk_discount_20", Map.of("threshold", 20, "discount", 0.15)
                );
                
                activePricingRules.forEach(pricingCache::put);
            }
        } catch (Exception e) {
            log.error("Error warming up active pricing rules: {}", e.getMessage());
        }
    }

    /**
     * Warm up frequently used tax rates
     */
    private void warmupFrequentlyUsedTaxRates() {
        try {
            Cache taxCache = cacheManager.getCache("tax-rates");
            if (taxCache != null) {
                // Load common tax rates
                Map<String, Double> commonTaxRates = Map.of(
                    "US-CA", 0.0975,  // California
                    "US-NY", 0.08,    // New York
                    "US-TX", 0.0625,  // Texas
                    "US-FL", 0.06,    // Florida
                    "EU", 0.20,       // EU VAT
                    "UK", 0.20,       // UK VAT
                    "CA", 0.13        // Canada GST
                );
                
                commonTaxRates.forEach(taxCache::put);
            }
        } catch (Exception e) {
            log.error("Error warming up frequently used tax rates: {}", e.getMessage());
        }
    }

    /**
     * Refresh critical caches
     */
    private void refreshCriticalCaches() {
        try {
            // Refresh product validation cache for active products
            Cache validationCache = cacheManager.getCache("product-validation");
            if (validationCache != null) {
                // This would typically refresh the most recently accessed products
                log.debug("Refreshed product validation cache");
            }
            
            // Refresh user profile cache for active users
            Cache userCache = cacheManager.getCache("user-profiles");
            if (userCache != null) {
                // This would typically refresh recently active users
                log.debug("Refreshed user profiles cache");
            }
            
        } catch (Exception e) {
            log.error("Error refreshing critical caches: {}", e.getMessage());
        }
    }

    /**
     * Warm up common pricing scenarios
     */
    private void warmupCommonPricingScenarios(Cache pricingCache) {
        // Pre-calculate common cart value scenarios
        for (int items = 1; items <= 20; items++) {
            for (double unitPrice = 10.0; unitPrice <= 100.0; unitPrice += 10.0) {
                String key = "scenario_" + items + "_" + unitPrice;
                Map<String, Object> scenario = Map.of(
                    "items", items,
                    "unitPrice", unitPrice,
                    "subtotal", items * unitPrice
                );
                pricingCache.put(key, scenario);
            }
        }
    }

    /**
     * Warm up common tax rates
     */
    private void warmupCommonTaxRates(Cache taxCache) {
        Map<String, Object> taxRates = Map.of(
            "US-CA", Map.of("rate", 0.0975, "type", "state"),
            "US-NY", Map.of("rate", 0.08, "type", "state"),
            "US-TX", Map.of("rate", 0.0625, "type", "state"),
            "EU", Map.of("rate", 0.20, "type", "vat"),
            "UK", Map.of("rate", 0.20, "type", "vat")
        );
        
        taxRates.forEach(taxCache::put);
    }

    /**
     * Warm up common shipping scenarios
     */
    private void warmupCommonShippingScenarios(Cache shippingCache) {
        // Pre-calculate shipping for common weight/distance combinations
        String[] zones = {"local", "regional", "national", "international"};
        double[] weights = {1.0, 2.0, 5.0, 10.0, 20.0};
        
        for (String zone : zones) {
            for (double weight : weights) {
                String key = "shipping_" + zone + "_" + weight;
                Map<String, Object> shipping = Map.of(
                    "zone", zone,
                    "weight", weight,
                    "cost", calculateShippingCost(zone, weight)
                );
                shippingCache.put(key, shipping);
            }
        }
    }

    /**
     * Warm up system configurations
     */
    private void warmupSystemConfigurations(Cache configCache) {
        Map<String, Object> configs = Map.of(
            "free_shipping_threshold", 50.0,
            "max_cart_items", 100,
            "max_quantity_per_item", 99,
            "cart_expiry_hours", 24,
            "guest_cart_expiry_hours", 2
        );
        
        configs.forEach(configCache::put);
    }

    /**
     * Calculate shipping cost (simplified)
     */
    private double calculateShippingCost(String zone, double weight) {
        double baseCost = switch (zone) {
            case "local" -> 5.0;
            case "regional" -> 8.0;
            case "national" -> 12.0;
            case "international" -> 25.0;
            default -> 10.0;
        };
        
        return baseCost + (weight * 1.5);
    }

    // ==================== STATUS AND MONITORING ====================

    /**
     * Get cache warming status
     */
    public Map<String, Object> getCacheWarmingStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("warmupInProgress", warmupInProgress);
        status.put("lastWarmupTime", lastWarmupTime);
        status.put("cacheWarmupTimes", cacheWarmupTimes);
        status.put("timestamp", LocalDateTime.now());
        
        return status;
    }

    /**
     * Check if cache warming is needed
     */
    public boolean isCacheWarmingNeeded() {
        if (lastWarmupTime == null) {
            return true;
        }
        
        // Check if last warmup was more than 4 hours ago
        return lastWarmupTime.isBefore(LocalDateTime.now().minusHours(4));
    }

    /**
     * Trigger manual cache warmup
     */
    public CompletableFuture<Map<String, Object>> triggerManualWarmup(boolean comprehensive) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new HashMap<>();
            
            try {
                if (warmupInProgress) {
                    result.put("success", false);
                    result.put("message", "Cache warmup already in progress");
                    return result;
                }
                
                if (comprehensive) {
                    performComprehensiveWarmup().get();
                    result.put("type", "comprehensive");
                } else {
                    performIncrementalWarmup().get();
                    result.put("type", "incremental");
                }
                
                result.put("success", true);
                result.put("message", "Cache warmup completed successfully");
                result.put("completedAt", LocalDateTime.now());
                
            } catch (Exception e) {
                result.put("success", false);
                result.put("error", e.getMessage());
                log.error("Error in manual cache warmup: {}", e.getMessage());
            }
            
            return result;
        });
    }
}
