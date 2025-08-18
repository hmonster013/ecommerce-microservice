package org.de013.shoppingcart.repository.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.entity.RedisCart;
import org.de013.shoppingcart.entity.enums.CartStatus;
import org.de013.shoppingcart.entity.enums.CartType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis-based session management for shopping carts
 * Handles guest sessions, user cart migration, and session cleanup
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisCartSessionManager {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisCartOperations redisCartOperations;

    // Session management keys
    private static final String SESSION_REGISTRY = "cart_sessions";
    private static final String USER_SESSIONS = "user_sessions:";
    private static final String SESSION_METADATA = "session_meta:";
    private static final String GUEST_CART_PREFIX = "guest_cart:";
    private static final String MIGRATION_LOCK = "migration_lock:";

    // ==================== SESSION LIFECYCLE ====================

    /**
     * Create new guest session
     */
    public String createGuestSession() {
        try {
            String sessionId = generateSessionId();
            String metaKey = SESSION_METADATA + sessionId;
            
            // Store session metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("sessionId", sessionId);
            metadata.put("createdAt", LocalDateTime.now().toString());
            metadata.put("type", "GUEST");
            metadata.put("status", "ACTIVE");
            
            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
            valueOps.set(metaKey, metadata, Duration.ofHours(2)); // 2 hour session timeout
            
            // Add to session registry
            SetOperations<String, Object> setOps = redisTemplate.opsForSet();
            setOps.add(SESSION_REGISTRY, sessionId);
            
            log.debug("Created guest session: {}", sessionId);
            return sessionId;
        } catch (Exception e) {
            log.error("Error creating guest session: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create guest session", e);
        }
    }

    /**
     * Associate session with user (login)
     */
    public boolean associateSessionWithUser(String sessionId, String userId) {
        try {
            String metaKey = SESSION_METADATA + sessionId;
            String userSessionsKey = USER_SESSIONS + userId;
            
            // Update session metadata
            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) valueOps.get(metaKey);
            
            if (metadata == null) {
                log.warn("Session metadata not found for session: {}", sessionId);
                return false;
            }
            
            metadata.put("userId", userId);
            metadata.put("type", "USER");
            metadata.put("associatedAt", LocalDateTime.now().toString());
            
            valueOps.set(metaKey, metadata, Duration.ofDays(30)); // Extend for logged-in users
            
            // Add to user sessions
            SetOperations<String, Object> setOps = redisTemplate.opsForSet();
            setOps.add(userSessionsKey, sessionId);
            redisTemplate.expire(userSessionsKey, Duration.ofDays(30));
            
            log.debug("Associated session {} with user {}", sessionId, userId);
            return true;
        } catch (Exception e) {
            log.error("Error associating session with user: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get session metadata
     */
    public Optional<Map<String, Object>> getSessionMetadata(String sessionId) {
        try {
            String metaKey = SESSION_METADATA + sessionId;
            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
            
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) valueOps.get(metaKey);
            
            return Optional.ofNullable(metadata);
        } catch (Exception e) {
            log.error("Error getting session metadata: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    // ==================== CART MIGRATION ====================

    /**
     * Migrate guest cart to user cart
     */
    public boolean migrateGuestCartToUser(String sessionId, String userId) {
        String migrationLockKey = MIGRATION_LOCK + sessionId + ":" + userId;
        
        try {
            // Acquire migration lock
            if (!redisCartOperations.acquireCartLock(migrationLockKey, Duration.ofMinutes(5))) {
                log.warn("Could not acquire migration lock for session {} to user {}", sessionId, userId);
                return false;
            }
            
            // Get guest cart
            Optional<RedisCart> guestCartOpt = redisCartOperations.getCartBySessionId(sessionId);
            if (guestCartOpt.isEmpty()) {
                log.debug("No guest cart found for session: {}", sessionId);
                return true; // No cart to migrate is success
            }
            
            RedisCart guestCart = guestCartOpt.get();
            
            // Check if user already has a cart
            Optional<RedisCart> userCartOpt = redisCartOperations.getCartByUserId(userId);
            
            if (userCartOpt.isPresent()) {
                // Merge carts
                return mergeGuestCartWithUserCart(guestCart, userCartOpt.get(), userId);
            } else {
                // Convert guest cart to user cart
                return convertGuestCartToUserCart(guestCart, userId);
            }
            
        } catch (Exception e) {
            log.error("Error migrating guest cart to user: {}", e.getMessage(), e);
            return false;
        } finally {
            // Release migration lock
            redisCartOperations.releaseCartLock(migrationLockKey);
        }
    }

    /**
     * Merge guest cart with existing user cart
     */
    private boolean mergeGuestCartWithUserCart(RedisCart guestCart, RedisCart userCart, String userId) {
        try {
            // Merge items from guest cart to user cart
            if (guestCart.getItems() != null) {
                for (RedisCart.RedisCartItem guestItem : guestCart.getItems()) {
                    // Check if item already exists in user cart
                    boolean itemExists = userCart.getItems().stream()
                            .anyMatch(userItem -> 
                                userItem.getProductId().equals(guestItem.getProductId()) &&
                                Objects.equals(userItem.getVariantId(), guestItem.getVariantId()));
                    
                    if (!itemExists) {
                        userCart.addItem(guestItem);
                    } else {
                        // Update quantity if item exists
                        userCart.getItems().stream()
                                .filter(userItem -> 
                                    userItem.getProductId().equals(guestItem.getProductId()) &&
                                    Objects.equals(userItem.getVariantId(), guestItem.getVariantId()))
                                .findFirst()
                                .ifPresent(userItem -> {
                                    userItem.setQuantity(userItem.getQuantity() + guestItem.getQuantity());
                                    userItem.calculateTotalPrice();
                                });
                    }
                }
            }
            
            // Update cart totals
            userCart.updateCartTotals();
            userCart.setCartType(CartType.USER);
            userCart.setUserId(userId);
            
            // Save merged cart
            redisCartOperations.saveCartWithTTL(userCart, Duration.ofSeconds(CartType.USER.getDefaultTtlSeconds()));
            
            // Delete guest cart
            redisCartOperations.deleteCart("session_cart:" + guestCart.getSessionId());
            
            log.debug("Merged guest cart {} with user cart for user {}", guestCart.getSessionId(), userId);
            return true;
            
        } catch (Exception e) {
            log.error("Error merging guest cart with user cart: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Convert guest cart to user cart
     */
    private boolean convertGuestCartToUserCart(RedisCart guestCart, String userId) {
        try {
            // Update cart properties
            guestCart.setUserId(userId);
            guestCart.setCartType(CartType.USER);
            guestCart.setUpdatedAt(LocalDateTime.now());
            
            // Save as user cart
            redisCartOperations.saveCartWithTTL(guestCart, Duration.ofSeconds(CartType.USER.getDefaultTtlSeconds()));
            
            // Delete guest cart
            redisCartOperations.deleteCart("session_cart:" + guestCart.getSessionId());
            
            log.debug("Converted guest cart {} to user cart for user {}", guestCart.getSessionId(), userId);
            return true;
            
        } catch (Exception e) {
            log.error("Error converting guest cart to user cart: {}", e.getMessage(), e);
            return false;
        }
    }

    // ==================== SESSION CLEANUP ====================

    /**
     * Clean up expired sessions
     */
    public int cleanupExpiredSessions() {
        try {
            SetOperations<String, Object> setOps = redisTemplate.opsForSet();
            Set<Object> allSessions = setOps.members(SESSION_REGISTRY);
            
            if (allSessions == null || allSessions.isEmpty()) {
                return 0;
            }
            
            int cleanedCount = 0;
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);
            
            for (Object sessionObj : allSessions) {
                String sessionId = sessionObj.toString();
                
                Optional<Map<String, Object>> metadataOpt = getSessionMetadata(sessionId);
                if (metadataOpt.isEmpty()) {
                    // Remove from registry if metadata is missing
                    setOps.remove(SESSION_REGISTRY, sessionId);
                    cleanedCount++;
                    continue;
                }
                
                Map<String, Object> metadata = metadataOpt.get();
                String createdAtStr = (String) metadata.get("createdAt");
                
                if (createdAtStr != null) {
                    LocalDateTime createdAt = LocalDateTime.parse(createdAtStr);
                    if (createdAt.isBefore(cutoffTime)) {
                        cleanupSession(sessionId);
                        cleanedCount++;
                    }
                }
            }
            
            log.debug("Cleaned up {} expired sessions", cleanedCount);
            return cleanedCount;
            
        } catch (Exception e) {
            log.error("Error cleaning up expired sessions: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Clean up specific session
     */
    public boolean cleanupSession(String sessionId) {
        try {
            // Remove session metadata
            String metaKey = SESSION_METADATA + sessionId;
            redisTemplate.delete(metaKey);
            
            // Remove from session registry
            SetOperations<String, Object> setOps = redisTemplate.opsForSet();
            setOps.remove(SESSION_REGISTRY, sessionId);
            
            // Delete associated guest cart
            redisCartOperations.deleteCart("session_cart:" + sessionId);
            
            log.debug("Cleaned up session: {}", sessionId);
            return true;
            
        } catch (Exception e) {
            log.error("Error cleaning up session {}: {}", sessionId, e.getMessage(), e);
            return false;
        }
    }

    // ==================== SESSION ANALYTICS ====================

    /**
     * Get active session count
     */
    public long getActiveSessionCount() {
        try {
            SetOperations<String, Object> setOps = redisTemplate.opsForSet();
            Long count = setOps.size(SESSION_REGISTRY);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("Error getting active session count: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Get user sessions
     */
    public Set<String> getUserSessions(String userId) {
        try {
            String userSessionsKey = USER_SESSIONS + userId;
            SetOperations<String, Object> setOps = redisTemplate.opsForSet();
            Set<Object> sessions = setOps.members(userSessionsKey);
            
            if (sessions == null) {
                return new HashSet<>();
            }
            
            return sessions.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("Error getting user sessions: {}", e.getMessage(), e);
            return new HashSet<>();
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Generate unique session ID
     */
    private String generateSessionId() {
        return "sess_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Check if session is valid
     */
    public boolean isSessionValid(String sessionId) {
        return getSessionMetadata(sessionId).isPresent();
    }

    /**
     * Extend session TTL
     */
    public boolean extendSessionTTL(String sessionId, Duration extension) {
        try {
            String metaKey = SESSION_METADATA + sessionId;
            return redisTemplate.expire(metaKey, extension);
        } catch (Exception e) {
            log.error("Error extending session TTL: {}", e.getMessage(), e);
            return false;
        }
    }
}
