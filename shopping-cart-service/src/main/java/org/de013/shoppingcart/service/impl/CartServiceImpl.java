package org.de013.shoppingcart.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.dto.request.ApplyCouponDto;
import org.de013.shoppingcart.dto.response.CartResponseDto;
import org.de013.shoppingcart.entity.Cart;
import org.de013.shoppingcart.entity.RedisCart;
import org.de013.shoppingcart.entity.enums.CartStatus;
import org.de013.shoppingcart.entity.enums.CartType;
import org.de013.shoppingcart.repository.jpa.CartRepository;
import org.de013.shoppingcart.repository.redis.RedisCartOperations;
import org.de013.shoppingcart.repository.redis.RedisCartRepository;
import org.de013.shoppingcart.repository.redis.RedisCartSessionManager;
import org.de013.shoppingcart.service.CartService;
import org.de013.shoppingcart.service.CartMergeService;
import org.de013.shoppingcart.service.CartItemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Core Cart Service Implementation
 * Handles main cart operations including CRUD, session management, and cart lifecycle
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final RedisCartRepository redisCartRepository;
    private final RedisCartOperations redisCartOperations;
    private final RedisCartSessionManager sessionManager;

    private final CartMergeService mergeService;
    private final CartItemService cartItemService;

    // ==================== CART CREATION & RETRIEVAL ====================

    /**
     * Get or create cart for user/session
     */
    @Override
    public CartResponseDto getOrCreateCart(String userId, String sessionId) {
        try {
            log.debug("Getting or creating cart for user: {}, session: {}", userId, sessionId);
            
            // Try to get existing cart
            Optional<CartResponseDto> existingCart = getActiveCart(userId, sessionId);
            if (existingCart.isPresent()) {
                return existingCart.get();
            }
            
            // Create new cart
            return createNewCart(userId, sessionId);
            
        } catch (Exception e) {
            log.error("Error getting or creating cart: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get or create cart", e);
        }
    }

    /**
     * Get active cart for user or session with Redis fallback
     */
    @Override
    public Optional<CartResponseDto> getActiveCart(String userId, String sessionId) {
        try {
            log.debug("Getting active cart for userId={}, sessionId={}", userId, sessionId);

            Optional<Cart> cart = Optional.empty();

            // Try to find cart with multiple strategies
            if (userId != null && sessionId != null) {
                // Strategy 1: Try to find cart with both userId and sessionId
                cart = cartRepository.findByUserIdAndSessionIdAndStatus(userId, sessionId, CartStatus.ACTIVE);
                log.debug("Strategy 1 (userId + sessionId): cart found = {}", cart.isPresent());

                // Strategy 2: If not found, try userId only
                if (cart.isEmpty()) {
                    cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);
                    log.debug("Strategy 2 (userId only): cart found = {}", cart.isPresent());
                }

                // Strategy 3: If still not found, try sessionId only
                if (cart.isEmpty()) {
                    cart = cartRepository.findBySessionIdAndStatus(sessionId, CartStatus.ACTIVE);
                    log.debug("Strategy 3 (sessionId only): cart found = {}", cart.isPresent());
                }
            } else if (userId != null) {
                // Only userId provided
                cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);
                log.debug("Strategy userId only: cart found = {}", cart.isPresent());
            } else if (sessionId != null) {
                // Only sessionId provided
                cart = cartRepository.findBySessionIdAndStatus(sessionId, CartStatus.ACTIVE);
                log.debug("Strategy sessionId only: cart found = {}", cart.isPresent());
            } else {
                log.debug("No userId or sessionId provided");
                return Optional.empty();
            }

            if (cart.isPresent()) {
                log.debug("Found cart: id={}, userId={}, sessionId={}",
                    cart.get().getId(), cart.get().getUserId(), cart.get().getSessionId());
                // Update last activity
                updateLastActivity(cart.get().getId());
                return Optional.of(convertToResponseDto(cart.get()));
            }

            log.debug("No active cart found for userId={}, sessionId={}", userId, sessionId);
            return Optional.empty();

        } catch (Exception e) {
            log.error("Error getting active cart for userId={}, sessionId={}: {}", userId, sessionId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Create new cart
     */
    @Override
    public CartResponseDto createNewCart(String userId, String sessionId) {
        try {
            log.debug("Creating new cart for user: {}, session: {}", userId, sessionId);
            
            // Determine cart type
            CartType cartType = userId != null ? CartType.USER : CartType.GUEST;
            
            // Create cart entity
            Cart cart = Cart.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .status(CartStatus.ACTIVE)
                    .cartType(cartType)
                    .currency("USD")
                    .subtotal(BigDecimal.ZERO)
                    .totalAmount(BigDecimal.ZERO)
                    .itemCount(0)
                    .totalQuantity(0)
                    .build();
            
            // Set expiration
            cart.setExpirationFromType();
            cart.setLastActivityAt(LocalDateTime.now());
            
            // Save to database
            cart = cartRepository.save(cart);
            
            // Save to Redis
            RedisCart redisCart = RedisCart.fromCart(cart);
            Duration ttl = Duration.ofSeconds(cartType.getDefaultTtlSeconds());
            redisCartOperations.saveCartWithTTL(redisCart, ttl);
            
            // Create session if needed
            if (sessionId != null && userId == null) {
                sessionManager.createGuestSession();
            }
            
            // Analytics removed for basic functionality
            
            log.info("Created new cart {} for user: {}, session: {}", cart.getId(), userId, sessionId);
            return convertToResponseDto(cart);
            
        } catch (Exception e) {
            log.error("Error creating new cart: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create new cart", e);
        }
    }

    /**
     * Get cart by ID
     */
    @Override
    public Optional<CartResponseDto> getCartById(Long cartId) {
        try {
            // Try Redis first
            Optional<RedisCart> redisCart = redisCartRepository.findByCartId(cartId);
            if (redisCart.isPresent()) {
                return Optional.of(convertToResponseDto(redisCart.get()));
            }
            
            // Fallback to database
            Optional<Cart> dbCart = cartRepository.findByIdWithItems(cartId);
            if (dbCart.isPresent()) {
                syncCartToRedis(dbCart.get());
                return Optional.of(convertToResponseDto(dbCart.get()));
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Error getting cart by ID {}: {}", cartId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    // ==================== CART UPDATES ====================

    /**
     * Update cart totals
     */
    @Override
    public void updateCartTotals(Long cartId) {
        try {
            log.debug("Updating totals for cart: {}", cartId);
            
            // Get cart items and calculate totals
            BigDecimal subtotal = cartItemService.calculateCartSubtotal(cartId);
            int itemCount = cartItemService.getCartItemCount(cartId);
            int totalQuantity = cartItemService.getCartTotalQuantity(cartId);
            
            // Update in database
            cartRepository.updateCartTotals(cartId, subtotal, subtotal, itemCount, totalQuantity, LocalDateTime.now());
            
            // Update in Redis
            Optional<RedisCart> redisCart = redisCartRepository.findByCartId(cartId);
            if (redisCart.isPresent()) {
                RedisCart cart = redisCart.get();
                cart.setSubtotal(subtotal);
                cart.setTotalAmount(subtotal);
                cart.setItemCount(itemCount);
                cart.setTotalQuantity(totalQuantity);
                cart.updateCartTotals();
                
                redisCartRepository.save(cart);
            }
            
            log.debug("Updated cart {} totals: subtotal={}, items={}, quantity={}", 
                     cartId, subtotal, itemCount, totalQuantity);
            
        } catch (Exception e) {
            log.error("Error updating cart totals for cart {}: {}", cartId, e.getMessage(), e);
            throw new RuntimeException("Failed to update cart totals", e);
        }
    }

    /**
     * Update last activity
     */
    @Override
    public void updateLastActivity(Long cartId) {
        try {
            cartRepository.updateLastActivity(cartId, LocalDateTime.now());
            redisCartOperations.updateLastActivity(cartId.toString());
        } catch (Exception e) {
            log.error("Error updating last activity for cart {}: {}", cartId, e.getMessage(), e);
        }
    }

    // ==================== COUPON MANAGEMENT ====================

    /**
     * Apply coupon to cart
     */
    @Override
    public CartResponseDto applyCoupon(ApplyCouponDto request) {
        try {
            log.debug("Applying coupon {} to cart", request.getCouponCode());
            
            // Get cart
            Optional<CartResponseDto> cartOpt = getActiveCart(request.getUserId(), request.getSessionId());
            if (cartOpt.isEmpty()) {
                throw new RuntimeException("Cart not found");
            }
            
            CartResponseDto cart = cartOpt.get();
            
            // Validate coupon (placeholder - would integrate with coupon service)
            BigDecimal discountAmount = calculateCouponDiscount(request.getCouponCode(), cart.getSubtotal());
            
            // Apply coupon
            cartRepository.applyCoupon(cart.getCartId(), request.getCouponCode(), discountAmount, LocalDateTime.now());
            
            // Update Redis
            Optional<RedisCart> redisCart = redisCartRepository.findByCartId(cart.getCartId());
            if (redisCart.isPresent()) {
                RedisCart rCart = redisCart.get();
                rCart.setCouponCode(request.getCouponCode());
                rCart.setDiscountAmount(discountAmount);
                rCart.updateCartTotals();
                redisCartRepository.save(rCart);
            }
            
            log.info("Applied coupon {} to cart {}, discount: {}", 
                    request.getCouponCode(), cart.getCartId(), discountAmount);
            
            return getCartById(cart.getCartId()).orElse(cart);
            
        } catch (Exception e) {
            log.error("Error applying coupon: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to apply coupon", e);
        }
    }

    /**
     * Remove coupon from cart
     */
    @Override
    public CartResponseDto removeCoupon(String userId, String sessionId) {
        try {
            Optional<CartResponseDto> cartOpt = getActiveCart(userId, sessionId);
            if (cartOpt.isEmpty()) {
                throw new RuntimeException("Cart not found");
            }
            
            CartResponseDto cart = cartOpt.get();
            
            // Remove coupon
            cartRepository.removeCoupon(cart.getCartId(), LocalDateTime.now());
            
            // Update Redis
            Optional<RedisCart> redisCart = redisCartRepository.findByCartId(cart.getCartId());
            if (redisCart.isPresent()) {
                RedisCart rCart = redisCart.get();
                rCart.setCouponCode(null);
                rCart.setDiscountAmount(BigDecimal.ZERO);
                rCart.updateCartTotals();
                redisCartRepository.save(rCart);
            }
            
            return getCartById(cart.getCartId()).orElse(cart);

        } catch (Exception e) {
            log.error("Error removing coupon: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to remove coupon", e);
        }
    }

    // ==================== CART LIFECYCLE ====================

    /**
     * Clear cart
     */
    @Override
    public CartResponseDto clearCart(String userId, String sessionId) {
        try {
            log.debug("Clearing cart for user: {}, session: {}", userId, sessionId);

            Optional<CartResponseDto> cartOpt = getActiveCart(userId, sessionId);
            if (cartOpt.isEmpty()) {
                throw new RuntimeException("Cart not found");
            }

            CartResponseDto cart = cartOpt.get();

            // Remove all items
            cartItemService.removeAllItems(cart.getCartId());

            // Update cart totals
            updateCartTotals(cart.getCartId());

            log.info("Cleared cart {}", cart.getCartId());
            return getCartById(cart.getCartId()).orElse(cart);

        } catch (Exception e) {
            log.error("Error clearing cart: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to clear cart", e);
        }
    }

    /**
     * Delete cart with improved handling for user/session scenarios
     */
    @Override
    public boolean deleteCart(String userId, String sessionId) {
        try {
            log.debug("Attempting to delete cart for userId={}, sessionId={}", userId, sessionId);

            // Validate input
            if (userId == null && sessionId == null) {
                log.warn("Both userId and sessionId are null, cannot delete cart");
                return false;
            }

            // Try to find cart to get cartId for database deletion
            Optional<CartResponseDto> cartOpt = getActiveCart(userId, sessionId);
            Long cartIdForDb = null;
            if (cartOpt.isPresent()) {
                cartIdForDb = cartOpt.get().getCartId();
            }

            // Delete from Redis using comprehensive cleanup
            // This handles both user and session carts, and potential key collisions
            boolean redisDeleted = redisCartOperations.deleteCartByIdentifiers(userId, sessionId);

            // Soft delete in database if we found a cart
            boolean dbDeleted = false;
            if (cartIdForDb != null) {
                try {
                    cartRepository.batchSoftDelete(List.of(cartIdForDb), LocalDateTime.now(), "USER_REQUEST");
                    dbDeleted = true;
                    log.debug("Soft deleted cart {} from database", cartIdForDb);
                } catch (Exception e) {
                    log.error("Error soft deleting cart {} from database: {}", cartIdForDb, e.getMessage(), e);
                }
            }

            // Consider operation successful if either Redis or DB deletion succeeded
            boolean success = redisDeleted || dbDeleted;

            if (success) {
                log.info("Successfully deleted cart for userId={}, sessionId={}, cartId={}", userId, sessionId, cartIdForDb);
            } else {
                log.warn("No cart found to delete for userId={}, sessionId={}", userId, sessionId);
            }

            return success;

        } catch (Exception e) {
            log.error("Error deleting cart for userId={}, sessionId={}: {}", userId, sessionId, e.getMessage(), e);
            return false;
        }
    }



    // ==================== HELPER METHODS ====================

    private Optional<RedisCart> getRedisCart(String userId, String sessionId) {
        if (userId != null) {
            return redisCartOperations.getCartByUserId(userId);
        } else if (sessionId != null) {
            return redisCartOperations.getCartBySessionId(sessionId);
        }
        return Optional.empty();
    }

    private Optional<Cart> getDatabaseCart(String userId, String sessionId) {
        if (userId != null) {
            return cartRepository.findByUserIdWithItems(userId, CartStatus.ACTIVE);
        } else if (sessionId != null) {
            return cartRepository.findBySessionIdAndStatus(sessionId, CartStatus.ACTIVE);
        }
        return Optional.empty();
    }

    private void syncCartToRedis(Cart cart) {
        try {
            RedisCart redisCart = RedisCart.fromCart(cart);
            Duration ttl = Duration.ofSeconds(cart.getCartType().getDefaultTtlSeconds());
            redisCartOperations.saveCartWithTTL(redisCart, ttl);
        } catch (Exception e) {
            log.error("Error syncing cart to Redis: {}", e.getMessage(), e);
        }
    }

    private CartResponseDto convertToResponseDto(Cart cart) {
        // Implementation would use MapStruct or manual mapping
        return CartResponseDto.builder()
                .cartId(cart.getId())
                .userId(cart.getUserId())
                .sessionId(cart.getSessionId())
                .status(cart.getStatus())
                .cartType(cart.getCartType())
                .currency(cart.getCurrency())
                .subtotal(cart.getSubtotal())
                .totalAmount(cart.getTotalAmount())
                .itemCount(cart.getItemCount())
                .totalQuantity(cart.getTotalQuantity())
                .couponCode(cart.getCouponCode())
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .lastActivityAt(cart.getLastActivityAt())
                .expiresAt(cart.getExpiresAt())
                .build();
    }

    private CartResponseDto convertToResponseDto(RedisCart redisCart) {
        return CartResponseDto.builder()
                .cartId(redisCart.getCartId())
                .userId(redisCart.getUserId())
                .sessionId(redisCart.getSessionId())
                .status(redisCart.getStatus())
                .cartType(redisCart.getCartType())
                .currency(redisCart.getCurrency())
                .subtotal(redisCart.getSubtotal())
                .totalAmount(redisCart.getTotalAmount())
                .itemCount(redisCart.getItemCount())
                .totalQuantity(redisCart.getTotalQuantity())
                .couponCode(redisCart.getCouponCode())
                .createdAt(redisCart.getCreatedAt())
                .updatedAt(redisCart.getUpdatedAt())
                .lastActivityAt(redisCart.getLastActivityAt())
                .expiresAt(redisCart.getExpiresAt())
                .build();
    }

    private BigDecimal calculateCouponDiscount(String couponCode, BigDecimal subtotal) {
        // Placeholder implementation - would integrate with coupon service
        if ("SAVE20".equals(couponCode)) {
            return subtotal.multiply(BigDecimal.valueOf(0.20));
        }
        return BigDecimal.ZERO;
    }
}
