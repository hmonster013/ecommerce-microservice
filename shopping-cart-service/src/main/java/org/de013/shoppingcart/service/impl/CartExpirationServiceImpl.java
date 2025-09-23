package org.de013.shoppingcart.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.entity.Cart;
import org.de013.shoppingcart.entity.enums.CartStatus;
import org.de013.shoppingcart.entity.enums.CartType;
import org.de013.shoppingcart.repository.jpa.CartRepository;
import org.de013.shoppingcart.service.CartExpirationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Cart Expiration Management Service Implementation
 * Handles cart expiration policies, cleanup, and lifecycle management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CartExpirationServiceImpl implements CartExpirationService {

    private final CartRepository cartRepository;

    // Configuration values for different cart types
    @Value("${shopping-cart.expiration.guest-cart-hours:2}")
    private int guestCartExpirationHours;

    @Value("${shopping-cart.expiration.user-cart-days:30}")
    private int userCartExpirationDays;

    @Value("${shopping-cart.expiration.saved-cart-days:90}")
    private int savedCartExpirationDays;

    @Value("${shopping-cart.expiration.wishlist-days:365}")
    private int wishlistExpirationDays;

    @Value("${shopping-cart.expiration.abandoned-cart-hours:24}")
    private int abandonedCartExpirationHours;

    @Value("${shopping-cart.expiration.cleanup-batch-size:100}")
    private int cleanupBatchSize;

    // ==================== EXPIRATION CALCULATION ====================

    /**
     * Calculate expiration time for a cart based on its type and status
     */
    @Override
    public LocalDateTime calculateExpirationTime(Cart cart) {
        LocalDateTime baseTime = cart.getLastActivityAt() != null ? 
            cart.getLastActivityAt() : cart.getCreatedAt();
        
        return switch (cart.getCartType()) {
            case GUEST -> baseTime.plusHours(guestCartExpirationHours);
            case USER -> {
                if (cart.getStatus() == CartStatus.ABANDONED) {
                    yield baseTime.plusHours(abandonedCartExpirationHours);
                } else {
                    yield baseTime.plusDays(userCartExpirationDays);
                }
            }
            case SAVED -> baseTime.plusDays(savedCartExpirationDays);
            case WISHLIST -> baseTime.plusDays(wishlistExpirationDays);
        };
    }

    /**
     * Check if a cart is expired
     */
    @Override
    public boolean isCartExpired(Cart cart) {
        if (cart.getExpiresAt() == null) {
            // Calculate expiration if not set
            cart.setExpiresAt(calculateExpirationTime(cart));
        }
        
        return LocalDateTime.now().isAfter(cart.getExpiresAt());
    }

    /**
     * Get time until cart expires
     */
    @Override
    public Map<String, Object> getTimeUntilExpiration(Cart cart) {
        Map<String, Object> result = new HashMap<>();
        result.put("cartId", cart.getId());
        result.put("currentTime", LocalDateTime.now());
        
        if (cart.getExpiresAt() == null) {
            cart.setExpiresAt(calculateExpirationTime(cart));
        }
        
        result.put("expiresAt", cart.getExpiresAt());
        result.put("isExpired", isCartExpired(cart));
        
        if (!isCartExpired(cart)) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiresAt = cart.getExpiresAt();
            
            long totalMinutes = java.time.Duration.between(now, expiresAt).toMinutes();
            long hours = totalMinutes / 60;
            long minutes = totalMinutes % 60;
            
            result.put("hoursUntilExpiration", hours);
            result.put("minutesUntilExpiration", minutes);
            result.put("totalMinutesUntilExpiration", totalMinutes);
        }
        
        return result;
    }

    // ==================== EXPIRATION PROCESSING ====================

    /**
     * Process expired carts
     */
    @Override
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    @Transactional
    public void processExpiredCarts() {
        try {
            log.debug("Starting expired cart cleanup process");
            
            LocalDateTime now = LocalDateTime.now();
            
            // Find expired carts in batches (simplified query)
            List<Cart> expiredCarts = cartRepository.findAll().stream()
                .filter(cart -> cart.getExpiresAt() != null && cart.getExpiresAt().isBefore(now))
                .filter(cart -> !cart.isDeleted())
                .limit(cleanupBatchSize)
                .toList();
            
            if (expiredCarts.isEmpty()) {
                log.debug("No expired carts found");
                return;
            }
            
            log.info("Found {} expired carts to process", expiredCarts.size());
            
            int processedCount = 0;
            int errorCount = 0;
            
            for (Cart cart : expiredCarts) {
                try {
                    processExpiredCart(cart);
                    processedCount++;
                } catch (Exception e) {
                    log.error("Error processing expired cart {}: {}", cart.getId(), e.getMessage(), e);
                    errorCount++;
                }
            }
            
            log.info("Expired cart cleanup completed: {} processed, {} errors", processedCount, errorCount);
            
        } catch (Exception e) {
            log.error("Error in expired cart cleanup process: {}", e.getMessage(), e);
        }
    }

    /**
     * Process individual expired cart
     */
    @Override
    @Transactional
    public void processExpiredCart(Cart cart) {
        try {
            log.debug("Processing expired cart: {} (type: {}, status: {})", 
                     cart.getId(), cart.getCartType(), cart.getStatus());
            
            // Stock reservation removed for basic functionality
            log.debug("Processing expired cart {} without stock reservation release", cart.getId());
            
            // Update cart status based on type and current status
            CartStatus newStatus = determineExpiredCartStatus(cart);
            cart.setStatus(newStatus);
            cart.setUpdatedAt(LocalDateTime.now());
            
            // For guest carts or truly expired carts, mark as deleted
            if (cart.getCartType() == CartType.GUEST || newStatus == CartStatus.DELETED) {
                cart.setDeleted(true);
                cart.setDeletedAt(LocalDateTime.now());
            }
            
            cartRepository.save(cart);
            
            log.info("Processed expired cart {}: status changed to {}", cart.getId(), newStatus);
            
        } catch (Exception e) {
            log.error("Error processing expired cart {}: {}", cart.getId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Determine new status for expired cart
     */
    private CartStatus determineExpiredCartStatus(Cart cart) {
        return switch (cart.getCartType()) {
            case GUEST -> CartStatus.DELETED;
            case USER -> {
                if (cart.getStatus() == CartStatus.ACTIVE) {
                    yield CartStatus.ABANDONED;
                } else {
                    yield CartStatus.EXPIRED;
                }
            }
            case SAVED -> CartStatus.EXPIRED;
            case WISHLIST -> CartStatus.EXPIRED;
        };
    }

    // ==================== CART EXTENSION ====================

    /**
     * Extend cart expiration time
     */
    @Override
    @Transactional
    public Map<String, Object> extendCartExpiration(Long cartId, int additionalHours) {
        try {
            log.debug("Extending expiration for cart {} by {} hours", cartId, additionalHours);
            
            Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("Cart not found: " + cartId));
            
            if (cart.getExpiresAt() == null) {
                cart.setExpiresAt(calculateExpirationTime(cart));
            }
            
            LocalDateTime newExpirationTime = cart.getExpiresAt().plusHours(additionalHours);
            cart.setExpiresAt(newExpirationTime);
            cart.setLastActivityAt(LocalDateTime.now());
            cart.setUpdatedAt(LocalDateTime.now());
            
            cartRepository.save(cart);
            
            Map<String, Object> result = new HashMap<>();
            result.put("cartId", cartId);
            result.put("success", true);
            result.put("newExpirationTime", newExpirationTime);
            result.put("additionalHours", additionalHours);
            result.put("timestamp", LocalDateTime.now());
            
            log.info("Extended cart {} expiration to {}", cartId, newExpirationTime);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error extending cart expiration for {}: {}", cartId, e.getMessage(), e);
            
            return Map.of(
                "cartId", cartId,
                "success", false,
                "error", e.getMessage()
            );
        }
    }

    /**
     * Refresh cart activity and extend expiration
     */
    @Override
    @Transactional
    public Map<String, Object> refreshCartActivity(Long cartId) {
        try {
            log.debug("Refreshing activity for cart: {}", cartId);
            
            Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("Cart not found: " + cartId));
            
            LocalDateTime now = LocalDateTime.now();
            cart.setLastActivityAt(now);
            cart.setUpdatedAt(now);
            
            // Recalculate expiration based on new activity time
            cart.setExpiresAt(calculateExpirationTime(cart));
            
            cartRepository.save(cart);
            
            Map<String, Object> result = new HashMap<>();
            result.put("cartId", cartId);
            result.put("success", true);
            result.put("lastActivityAt", now);
            result.put("newExpirationTime", cart.getExpiresAt());
            result.put("timestamp", now);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error refreshing cart activity for {}: {}", cartId, e.getMessage(), e);

            return Map.of(
                "cartId", cartId,
                "success", false,
                "error", e.getMessage()
            );
        }
    }

    // ==================== BULK OPERATIONS ====================

    /**
     * Get expiration status for multiple carts
     */
    @Override
    @Async
    public CompletableFuture<Map<String, Object>> getMultipleCartExpirationStatus(List<Long> cartIds) {
        try {
            log.debug("Getting expiration status for {} carts", cartIds.size());

            Map<String, Object> result = new HashMap<>();
            result.put("timestamp", LocalDateTime.now());
            result.put("totalCarts", cartIds.size());

            Map<Long, Map<String, Object>> cartStatuses = new HashMap<>();
            int expiredCount = 0;
            int soonToExpireCount = 0;

            for (Long cartId : cartIds) {
                try {
                    Cart cart = cartRepository.findById(cartId).orElse(null);
                    if (cart != null) {
                        Map<String, Object> status = getTimeUntilExpiration(cart);
                        cartStatuses.put(cartId, status);

                        if (Boolean.TRUE.equals(status.get("isExpired"))) {
                            expiredCount++;
                        } else {
                            Long minutesUntilExpiration = (Long) status.get("totalMinutesUntilExpiration");
                            if (minutesUntilExpiration != null && minutesUntilExpiration < 60) {
                                soonToExpireCount++;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error getting expiration status for cart {}: {}", cartId, e.getMessage());
                }
            }

            result.put("cartStatuses", cartStatuses);
            result.put("expiredCount", expiredCount);
            result.put("soonToExpireCount", soonToExpireCount);

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("Error getting multiple cart expiration status: {}", e.getMessage(), e);

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            errorResult.put("timestamp", LocalDateTime.now());
            return CompletableFuture.completedFuture(errorResult);
        }
    }

    // ==================== CLEANUP OPERATIONS ====================

    /**
     * Clean up old deleted carts
     */
    @Override
    @Scheduled(cron = "0 0 2 * * ?") // Run daily at 2 AM
    @Transactional
    public void cleanupOldDeletedCarts() {
        try {
            log.info("Starting cleanup of old deleted carts");

            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7); // Keep deleted carts for 7 days

            List<Cart> oldDeletedCarts = cartRepository.findAll().stream()
                .filter(cart -> cart.isDeleted() && cart.getDeletedAt() != null)
                .filter(cart -> cart.getDeletedAt().isBefore(cutoffDate))
                .limit(cleanupBatchSize)
                .toList();

            if (oldDeletedCarts.isEmpty()) {
                log.debug("No old deleted carts found for cleanup");
                return;
            }

            log.info("Found {} old deleted carts to remove", oldDeletedCarts.size());

            for (Cart cart : oldDeletedCarts) {
                try {
                    cartRepository.delete(cart);
                } catch (Exception e) {
                    log.error("Error deleting old cart {}: {}", cart.getId(), e.getMessage());
                }
            }

            log.info("Cleanup of old deleted carts completed");

        } catch (Exception e) {
            log.error("Error in old deleted carts cleanup: {}", e.getMessage(), e);
        }
    }

    // ==================== STATISTICS ====================

    /**
     * Get cart expiration statistics
     */
    @Override
    public Map<String, Object> getExpirationStatistics() {
        try {
            log.debug("Getting cart expiration statistics");

            LocalDateTime now = LocalDateTime.now();

            Map<String, Object> stats = new HashMap<>();
            stats.put("timestamp", now);

            // Count carts by expiration status (simplified queries)
            long expiredCount = cartRepository.findAll().stream()
                .filter(cart -> cart.getExpiresAt() != null && cart.getExpiresAt().isBefore(now))
                .filter(cart -> !cart.isDeleted())
                .count();

            long soonToExpireCount = cartRepository.findAll().stream()
                .filter(cart -> cart.getExpiresAt() != null)
                .filter(cart -> cart.getExpiresAt().isAfter(now) && cart.getExpiresAt().isBefore(now.plusHours(1)))
                .filter(cart -> !cart.isDeleted())
                .count();

            long activeCount = cartRepository.findAll().stream()
                .filter(cart -> cart.getStatus() == CartStatus.ACTIVE)
                .filter(cart -> !cart.isDeleted())
                .filter(cart -> cart.getExpiresAt() == null || cart.getExpiresAt().isAfter(now))
                .count();

            stats.put("expiredCarts", expiredCount);
            stats.put("soonToExpireCarts", soonToExpireCount);
            stats.put("activeCarts", activeCount);
            stats.put("totalCarts", expiredCount + soonToExpireCount + activeCount);

            // Count by cart type (simplified)
            Map<String, Long> countsByType = new HashMap<>();
            for (CartType type : CartType.values()) {
                long count = cartRepository.findAll().stream()
                    .filter(cart -> cart.getCartType() == type)
                    .filter(cart -> !cart.isDeleted())
                    .count();
                countsByType.put(type.name(), count);
            }
            stats.put("countsByType", countsByType);

            return stats;

        } catch (Exception e) {
            log.error("Error getting expiration statistics: {}", e.getMessage(), e);

            return Map.of(
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
        }
    }
}
