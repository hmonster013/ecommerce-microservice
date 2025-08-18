package org.de013.shoppingcart.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.entity.Cart;
import org.de013.shoppingcart.entity.CartItem;
import org.de013.shoppingcart.repository.jpa.CartRepository;
import org.de013.shoppingcart.repository.jpa.CartItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Database Optimization Service
 * Handles query optimization, batch operations, and database performance monitoring
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseOptimizationService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final EntityManager entityManager;
    private final PerformanceMonitoringService performanceMonitoringService;

    // ==================== QUERY OPTIMIZATION ====================

    /**
     * Optimized cart retrieval with eager loading
     */
    @Transactional(readOnly = true)
    public Cart findCartWithItemsOptimized(Long cartId) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Use custom query with JOIN FETCH to avoid N+1 problem
            String jpql = """
                SELECT c FROM Cart c 
                LEFT JOIN FETCH c.cartItems ci 
                WHERE c.id = :cartId AND c.deleted = false
                """;
            
            Cart cart = entityManager.createQuery(jpql, Cart.class)
                .setParameter("cartId", cartId)
                .getSingleResult();
            
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitoringService.trackDatabaseQuery("findCartWithItems", 
                java.time.Duration.ofMillis(duration), cart != null ? 1 : 0);
            
            log.debug("Optimized cart retrieval completed in {}ms", duration);
            return cart;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitoringService.trackDatabaseQuery("findCartWithItems", 
                java.time.Duration.ofMillis(duration), 0);
            
            log.error("Error in optimized cart retrieval: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Batch load multiple carts with items
     */
    @Transactional(readOnly = true)
    public List<Cart> findCartsWithItemsBatch(List<Long> cartIds) {
        long startTime = System.currentTimeMillis();
        
        try {
            if (cartIds.isEmpty()) {
                return List.of();
            }
            
            // Batch load carts with items in single query
            String jpql = """
                SELECT DISTINCT c FROM Cart c 
                LEFT JOIN FETCH c.cartItems ci 
                WHERE c.id IN :cartIds AND c.deleted = false
                ORDER BY c.id
                """;
            
            List<Cart> carts = entityManager.createQuery(jpql, Cart.class)
                .setParameter("cartIds", cartIds)
                .getResultList();
            
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitoringService.trackDatabaseQuery("findCartsWithItemsBatch", 
                java.time.Duration.ofMillis(duration), carts.size());
            
            log.debug("Batch cart retrieval completed in {}ms for {} carts", duration, carts.size());
            return carts;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitoringService.trackDatabaseQuery("findCartsWithItemsBatch", 
                java.time.Duration.ofMillis(duration), 0);
            
            log.error("Error in batch cart retrieval: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Optimized user carts query with pagination
     */
    @Transactional(readOnly = true)
    public Page<Cart> findUserCartsOptimized(String userId, Pageable pageable) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Use repository method with optimized query
            Page<Cart> carts = cartRepository.findByUserIdAndDeletedFalseOrderByLastActivityAtDesc(userId, pageable);
            
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitoringService.trackDatabaseQuery("findUserCartsOptimized", 
                java.time.Duration.ofMillis(duration), (int) carts.getTotalElements());
            
            log.debug("Optimized user carts query completed in {}ms", duration);
            return carts;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitoringService.trackDatabaseQuery("findUserCartsOptimized", 
                java.time.Duration.ofMillis(duration), 0);
            
            log.error("Error in optimized user carts query: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Optimized cart summary query
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCartSummaryOptimized(Long cartId) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Single query to get cart summary data
            String sql = """
                SELECT 
                    c.id as cart_id,
                    c.item_count,
                    c.total_quantity,
                    c.subtotal,
                    c.tax_amount,
                    c.shipping_amount,
                    c.discount_amount,
                    c.total_amount,
                    COUNT(ci.id) as actual_item_count,
                    SUM(ci.quantity) as actual_total_quantity,
                    SUM(ci.total_price) as actual_subtotal
                FROM carts c 
                LEFT JOIN cart_items ci ON c.id = ci.cart_id AND ci.deleted = false
                WHERE c.id = ? AND c.deleted = false
                GROUP BY c.id
                """;
            
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter(1, cartId);
            
            Object[] result = (Object[]) query.getSingleResult();
            
            Map<String, Object> summary = new HashMap<>();
            summary.put("cartId", result[0]);
            summary.put("itemCount", result[1]);
            summary.put("totalQuantity", result[2]);
            summary.put("subtotal", result[3]);
            summary.put("taxAmount", result[4]);
            summary.put("shippingAmount", result[5]);
            summary.put("discountAmount", result[6]);
            summary.put("totalAmount", result[7]);
            summary.put("actualItemCount", result[8]);
            summary.put("actualTotalQuantity", result[9]);
            summary.put("actualSubtotal", result[10]);
            
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitoringService.trackDatabaseQuery("getCartSummaryOptimized", 
                java.time.Duration.ofMillis(duration), 1);
            
            log.debug("Optimized cart summary query completed in {}ms", duration);
            return summary;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitoringService.trackDatabaseQuery("getCartSummaryOptimized", 
                java.time.Duration.ofMillis(duration), 0);
            
            log.error("Error in optimized cart summary query: {}", e.getMessage());
            throw e;
        }
    }

    // ==================== BATCH OPERATIONS ====================

    /**
     * Batch insert cart items
     */
    @Transactional
    public List<CartItem> batchInsertCartItems(List<CartItem> cartItems) {
        long startTime = System.currentTimeMillis();
        
        try {
            if (cartItems.isEmpty()) {
                return List.of();
            }
            
            // Process in batches of 50
            int batchSize = 50;
            List<CartItem> savedItems = cartItems.stream()
                .collect(Collectors.groupingBy(item -> cartItems.indexOf(item) / batchSize))
                .values()
                .stream()
                .flatMap(batch -> {
                    // Save batch
                    List<CartItem> saved = cartItemRepository.saveAll(batch);
                    entityManager.flush();
                    entityManager.clear(); // Clear persistence context to avoid memory issues
                    return saved.stream();
                })
                .collect(Collectors.toList());
            
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitoringService.trackDatabaseQuery("batchInsertCartItems", 
                java.time.Duration.ofMillis(duration), savedItems.size());
            
            log.debug("Batch insert of {} cart items completed in {}ms", savedItems.size(), duration);
            return savedItems;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitoringService.trackDatabaseQuery("batchInsertCartItems", 
                java.time.Duration.ofMillis(duration), 0);
            
            log.error("Error in batch insert cart items: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Batch update cart items
     */
    @Transactional
    public void batchUpdateCartItems(List<CartItem> cartItems) {
        long startTime = System.currentTimeMillis();
        
        try {
            if (cartItems.isEmpty()) {
                return;
            }
            
            // Use batch update query for better performance
            String jpql = """
                UPDATE CartItem ci SET 
                    ci.quantity = :quantity,
                    ci.unitPrice = :unitPrice,
                    ci.totalPrice = :totalPrice,
                    ci.updatedAt = :updatedAt
                WHERE ci.id = :id
                """;
            
            Query query = entityManager.createQuery(jpql);
            LocalDateTime now = LocalDateTime.now();
            
            int batchSize = 50;
            int count = 0;
            
            for (CartItem item : cartItems) {
                query.setParameter("quantity", item.getQuantity());
                query.setParameter("unitPrice", item.getUnitPrice());
                query.setParameter("totalPrice", item.getTotalPrice());
                query.setParameter("updatedAt", now);
                query.setParameter("id", item.getId());
                
                query.executeUpdate();
                
                if (++count % batchSize == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }
            }
            
            entityManager.flush();
            entityManager.clear();
            
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitoringService.trackDatabaseQuery("batchUpdateCartItems", 
                java.time.Duration.ofMillis(duration), cartItems.size());
            
            log.debug("Batch update of {} cart items completed in {}ms", cartItems.size(), duration);
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitoringService.trackDatabaseQuery("batchUpdateCartItems", 
                java.time.Duration.ofMillis(duration), 0);
            
            log.error("Error in batch update cart items: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Batch delete expired carts
     */
    @Transactional
    public int batchDeleteExpiredCarts(LocalDateTime expirationThreshold) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Soft delete expired carts
            String jpql = """
                UPDATE Cart c SET 
                    c.deleted = true,
                    c.deletedAt = :deletedAt,
                    c.updatedAt = :updatedAt
                WHERE c.expiresAt < :threshold AND c.deleted = false
                """;
            
            LocalDateTime now = LocalDateTime.now();
            int deletedCount = entityManager.createQuery(jpql)
                .setParameter("deletedAt", now)
                .setParameter("updatedAt", now)
                .setParameter("threshold", expirationThreshold)
                .executeUpdate();
            
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitoringService.trackDatabaseQuery("batchDeleteExpiredCarts", 
                java.time.Duration.ofMillis(duration), deletedCount);
            
            log.info("Batch deleted {} expired carts in {}ms", deletedCount, duration);
            return deletedCount;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitoringService.trackDatabaseQuery("batchDeleteExpiredCarts", 
                java.time.Duration.ofMillis(duration), 0);
            
            log.error("Error in batch delete expired carts: {}", e.getMessage());
            throw e;
        }
    }

    // ==================== ASYNC OPERATIONS ====================

    /**
     * Async cart statistics calculation
     */
    public CompletableFuture<Map<String, Object>> calculateCartStatisticsAsync(String userId) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                String sql = """
                    SELECT 
                        COUNT(*) as total_carts,
                        COUNT(CASE WHEN c.status = 'ACTIVE' THEN 1 END) as active_carts,
                        COUNT(CASE WHEN c.status = 'ABANDONED' THEN 1 END) as abandoned_carts,
                        COUNT(CASE WHEN c.status = 'CONVERTED' THEN 1 END) as converted_carts,
                        AVG(c.total_amount) as avg_cart_value,
                        SUM(c.total_amount) as total_cart_value,
                        AVG(c.item_count) as avg_items_per_cart
                    FROM carts c 
                    WHERE c.user_id = ? AND c.deleted = false
                    """;
                
                Query query = entityManager.createNativeQuery(sql);
                query.setParameter(1, userId);
                
                Object[] result = (Object[]) query.getSingleResult();
                
                Map<String, Object> stats = new HashMap<>();
                stats.put("totalCarts", result[0]);
                stats.put("activeCarts", result[1]);
                stats.put("abandonedCarts", result[2]);
                stats.put("convertedCarts", result[3]);
                stats.put("avgCartValue", result[4]);
                stats.put("totalCartValue", result[5]);
                stats.put("avgItemsPerCart", result[6]);
                
                long duration = System.currentTimeMillis() - startTime;
                performanceMonitoringService.trackDatabaseQuery("calculateCartStatisticsAsync", 
                    java.time.Duration.ofMillis(duration), 1);
                
                log.debug("Async cart statistics calculation completed in {}ms", duration);
                return stats;
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                performanceMonitoringService.trackDatabaseQuery("calculateCartStatisticsAsync", 
                    java.time.Duration.ofMillis(duration), 0);
                
                log.error("Error in async cart statistics calculation: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    // ==================== DATABASE HEALTH CHECK ====================

    /**
     * Check database performance health
     */
    public Map<String, Object> checkDatabaseHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Simple query to test database connectivity and performance
            String sql = "SELECT COUNT(*) FROM carts WHERE deleted = false";
            Query query = entityManager.createNativeQuery(sql);
            Object result = query.getSingleResult();
            
            long duration = System.currentTimeMillis() - startTime;
            
            health.put("status", "UP");
            health.put("responseTime", duration + "ms");
            health.put("totalActiveCarts", result);
            health.put("timestamp", LocalDateTime.now());
            
            // Check if response time is acceptable
            if (duration > 1000) { // More than 1 second
                health.put("warning", "Slow database response time");
            }
            
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            health.put("timestamp", LocalDateTime.now());
            log.error("Database health check failed: {}", e.getMessage());
        }
        
        return health;
    }

    /**
     * Get database performance statistics
     */
    public Map<String, Object> getDatabasePerformanceStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // Get table sizes
            String tableSizesSql = """
                SELECT 
                    'carts' as table_name,
                    COUNT(*) as row_count
                FROM carts
                UNION ALL
                SELECT 
                    'cart_items' as table_name,
                    COUNT(*) as row_count
                FROM cart_items
                UNION ALL
                SELECT 
                    'cart_summaries' as table_name,
                    COUNT(*) as row_count
                FROM cart_summaries
                """;
            
            Query query = entityManager.createNativeQuery(tableSizesSql);
            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();
            
            Map<String, Object> tableSizes = new HashMap<>();
            for (Object[] row : results) {
                tableSizes.put((String) row[0], row[1]);
            }
            
            stats.put("tableSizes", tableSizes);
            stats.put("timestamp", LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Error getting database performance stats: {}", e.getMessage());
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }
}
