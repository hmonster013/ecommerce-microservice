package org.de013.shoppingcart.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.client.ProductCatalogFeignClient;
import org.de013.shoppingcart.client.UserServiceFeignClient;
import org.de013.shoppingcart.dto.ProductInfo;
import org.de013.shoppingcart.dto.response.CartValidationDto;
import org.de013.shoppingcart.entity.Cart;
import org.de013.shoppingcart.entity.CartItem;
import org.de013.shoppingcart.entity.enums.CartStatus;
import org.de013.shoppingcart.repository.jpa.CartItemRepository;
import org.de013.shoppingcart.repository.jpa.CartRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cart Validation Service
 * Handles comprehensive cart and item validation including stock, pricing, and business rules
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CartValidationService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductCatalogClient productCatalogClient;
    private final ProductCatalogFeignClient productCatalogFeignClient;
    private final UserServiceFeignClient userServiceFeignClient;

    // Configuration values
    @Value("${shopping-cart.validation.max-items-per-cart:100}")
    private int maxItemsPerCart;

    @Value("${shopping-cart.validation.max-quantity-per-item:99}")
    private int maxQuantityPerItem;

    @Value("${shopping-cart.validation.max-cart-value:10000.00}")
    private BigDecimal maxCartValue;

    @Value("${shopping-cart.validation.min-item-price:0.01}")
    private BigDecimal minItemPrice;

    @Value("${shopping-cart.validation.price-change-threshold:0.01}")
    private BigDecimal priceChangeThreshold;

    @Value("${shopping-cart.validation.cart-expiry-hours:24}")
    private int cartExpiryHours;
    private final PricingService pricingService;

    // Validation constants
    private static final int MAX_ITEMS_PER_CART = 100;
    private static final int MAX_QUANTITY_PER_ITEM = 99;
    private static final BigDecimal MAX_CART_VALUE = new BigDecimal("10000.00");
    private static final BigDecimal MIN_ITEM_PRICE = new BigDecimal("0.01");

    // ==================== QUANTITY LIMITS ENFORCEMENT ====================

    /**
     * Enforce quantity limits for cart item
     */
    public Map<String, Object> enforceQuantityLimits(Long cartId, Long itemId, Integer requestedQuantity) {
        try {
            log.debug("Enforcing quantity limits for cart: {} item: {} quantity: {}", cartId, itemId, requestedQuantity);

            Map<String, Object> result = new HashMap<>();
            result.put("cartId", cartId);
            result.put("itemId", itemId);
            result.put("requestedQuantity", requestedQuantity);
            result.put("timestamp", LocalDateTime.now());

            List<String> violations = new ArrayList<>();

            // Check minimum quantity
            if (requestedQuantity <= 0) {
                violations.add("Quantity must be greater than 0");
                result.put("valid", false);
                result.put("violations", violations);
                return result;
            }

            // Check maximum quantity per item
            if (requestedQuantity > maxQuantityPerItem) {
                violations.add("Quantity exceeds maximum allowed per item: " + maxQuantityPerItem);
            }

            // Get cart to check total items
            Optional<Cart> cartOpt = cartRepository.findById(cartId);
            if (cartOpt.isPresent()) {
                Cart cart = cartOpt.get();

                // Check maximum items per cart
                if (cart.getCartItems().size() >= maxItemsPerCart) {
                    violations.add("Cart exceeds maximum number of items: " + maxItemsPerCart);
                }

                // Check if adding this quantity would exceed cart value limit
                BigDecimal currentCartValue = cart.getTotalAmount() != null ? cart.getTotalAmount() : BigDecimal.ZERO;

                // Find the item to get its price
                CartItem targetItem = cart.getCartItems().stream()
                    .filter(item -> item.getId().equals(itemId))
                    .findFirst()
                    .orElse(null);

                if (targetItem != null) {
                    BigDecimal itemValue = targetItem.getUnitPrice().multiply(BigDecimal.valueOf(requestedQuantity));
                    BigDecimal projectedCartValue = currentCartValue.add(itemValue);

                    if (projectedCartValue.compareTo(maxCartValue) > 0) {
                        violations.add("Adding this quantity would exceed maximum cart value: $" + maxCartValue);
                    }

                    // Check product-specific quantity limits
                    Map<String, Object> productLimits = checkProductQuantityLimits(targetItem.getProductId(), requestedQuantity);
                    if (!Boolean.TRUE.equals(productLimits.get("valid"))) {
                        @SuppressWarnings("unchecked")
                        List<String> productViolations = (List<String>) productLimits.get("violations");
                        violations.addAll(productViolations);
                    }
                }
            }

            result.put("valid", violations.isEmpty());
            result.put("violations", violations);
            result.put("maxQuantityPerItem", maxQuantityPerItem);
            result.put("maxItemsPerCart", maxItemsPerCart);
            result.put("maxCartValue", maxCartValue);

            return result;

        } catch (Exception e) {
            log.error("Error enforcing quantity limits: {}", e.getMessage(), e);
            return Map.of(
                "cartId", cartId,
                "itemId", itemId,
                "valid", false,
                "error", e.getMessage()
            );
        }
    }

    /**
     * Check product-specific quantity limits
     */
    private Map<String, Object> checkProductQuantityLimits(String productId, Integer requestedQuantity) {
        try {
            // Check with product catalog for product-specific limits
            Map<String, Object> productInfo = productCatalogFeignClient.validateProduct(productId);

            Map<String, Object> result = new HashMap<>();
            result.put("productId", productId);
            result.put("valid", true);
            result.put("violations", new ArrayList<String>());

            if (Boolean.TRUE.equals(productInfo.get("fallback"))) {
                // If product service is unavailable, use default limits
                return result;
            }

            // Check if product has specific quantity limits
            Integer maxQuantityPerOrder = (Integer) productInfo.get("maxQuantityPerOrder");
            if (maxQuantityPerOrder != null && requestedQuantity > maxQuantityPerOrder) {
                @SuppressWarnings("unchecked")
                List<String> violations = (List<String>) result.get("violations");
                violations.add("Quantity exceeds product-specific limit: " + maxQuantityPerOrder);
                result.put("valid", false);
            }

            return result;

        } catch (Exception e) {
            log.warn("Error checking product quantity limits for {}: {}", productId, e.getMessage());
            return Map.of("productId", productId, "valid", true, "violations", List.of());
        }
    }

    // ==================== CART VALIDATION ====================

    /**
     * Validate entire cart
     */
    public CartValidationDto validateCart(Long cartId) {
        try {
            log.debug("Validating cart: {}", cartId);
            
            Optional<Cart> cartOpt = cartRepository.findByIdWithItems(cartId);
            if (cartOpt.isEmpty()) {
                return createErrorValidation("Cart not found");
            }
            
            Cart cart = cartOpt.get();
            CartValidationDto.CartValidationDtoBuilder validationBuilder = CartValidationDto.builder();
            
            List<CartValidationDto.ValidationMessageDto> generalMessages = new ArrayList<>();
            List<CartValidationDto.ItemValidationDto> itemValidations = new ArrayList<>();
            
            // Validate cart basic properties
            validateCartBasics(cart, generalMessages);
            
            // Validate cart items
            for (CartItem item : cart.getCartItems()) {
                CartValidationDto.ItemValidationDto itemValidation = validateCartItem(item);
                itemValidations.add(itemValidation);
            }
            
            // Validate pricing
            CartValidationDto.PricingValidationDto pricingValidation = validatePricing(cart);
            
            // Validate inventory
            CartValidationDto.InventoryValidationDto inventoryValidation = validateInventory(cart);
            
            // Validate shipping
            CartValidationDto.ShippingValidationDto shippingValidation = validateShipping(cart);
            
            // Validate coupon if applied
            CartValidationDto.CouponValidationDto couponValidation = null;
            if (cart.getCouponCode() != null) {
                couponValidation = validateCoupon(cart);
            }
            
            // Calculate overall validation status
            boolean hasErrors = generalMessages.stream().anyMatch(msg -> "ERROR".equals(msg.getType())) ||
                               itemValidations.stream().anyMatch(item -> "ERROR".equals(item.getValidationStatus())) ||
                               !pricingValidation.getIsValid() ||
                               !inventoryValidation.getIsSufficient() ||
                               !shippingValidation.getIsAvailable() ||
                               (couponValidation != null && !couponValidation.getIsValid());
            
            boolean hasWarnings = generalMessages.stream().anyMatch(msg -> "WARNING".equals(msg.getType())) ||
                                 itemValidations.stream().anyMatch(item -> "WARNING".equals(item.getValidationStatus()));
            
            String validationStatus = hasErrors ? "ERROR" : (hasWarnings ? "WARNING" : "VALID");
            
            return validationBuilder
                    .validationStatus(validationStatus)
                    .isValid(!hasErrors)
                    .hasErrors(hasErrors)
                    .hasWarnings(hasWarnings)
                    .validationTimestamp(LocalDateTime.now())
                    .generalMessages(generalMessages)
                    .itemMessages(itemValidations)
                    .pricingValidation(pricingValidation)
                    .inventoryValidation(inventoryValidation)
                    .shippingValidation(shippingValidation)
                    .couponValidation(couponValidation)
                    .errorCount((int) generalMessages.stream().filter(msg -> "ERROR".equals(msg.getType())).count())
                    .warningCount((int) generalMessages.stream().filter(msg -> "WARNING".equals(msg.getType())).count())
                    .validationScore(calculateValidationScore(hasErrors, hasWarnings))
                    .build();
            
        } catch (Exception e) {
            log.error("Error validating cart {}: {}", cartId, e.getMessage(), e);
            return createErrorValidation("Validation failed: " + e.getMessage());
        }
    }

    /**
     * Validate cart basic properties
     */
    private void validateCartBasics(Cart cart, List<CartValidationDto.ValidationMessageDto> messages) {
        // Check if cart is expired
        if (cart.isExpired()) {
            messages.add(createValidationMessage("ERROR", "CART_EXPIRED", 
                "Cart has expired", "expires_at", "Please refresh your cart"));
        }
        
        // Check if cart can be modified
        if (!cart.canBeModified()) {
            messages.add(createValidationMessage("ERROR", "CART_NOT_MODIFIABLE", 
                "Cart cannot be modified", "status", "Cart is in read-only state"));
        }
        
        // Check item count limits
        if (cart.getItemCount() > MAX_ITEMS_PER_CART) {
            messages.add(createValidationMessage("ERROR", "TOO_MANY_ITEMS", 
                "Too many items in cart", "item_count", "Remove some items to continue"));
        }
        
        // Check cart value limits
        if (cart.getTotalAmount().compareTo(MAX_CART_VALUE) > 0) {
            messages.add(createValidationMessage("ERROR", "CART_VALUE_TOO_HIGH", 
                "Cart value exceeds maximum limit", "total_amount", "Remove some items to continue"));
        }
        
        // Check if cart is empty
        if (cart.isEmpty()) {
            messages.add(createValidationMessage("WARNING", "CART_EMPTY", 
                "Cart is empty", "item_count", "Add items to your cart"));
        }
    }

    // ==================== ITEM VALIDATION ====================

    /**
     * Validate single cart item
     */
    public CartValidationDto.ItemValidationDto validateCartItem(CartItem item) {
        List<CartValidationDto.ValidationMessageDto> messages = new ArrayList<>();
        
        // Validate quantity
        if (item.getQuantity() <= 0) {
            messages.add(createValidationMessage("ERROR", "INVALID_QUANTITY", 
                "Quantity must be greater than 0", "quantity", "Update item quantity"));
        }
        
        if (item.getQuantity() > MAX_QUANTITY_PER_ITEM) {
            messages.add(createValidationMessage("ERROR", "QUANTITY_TOO_HIGH", 
                "Quantity exceeds maximum allowed", "quantity", "Reduce item quantity"));
        }
        
        // Validate price
        if (item.getUnitPrice().compareTo(MIN_ITEM_PRICE) < 0) {
            messages.add(createValidationMessage("ERROR", "INVALID_PRICE", 
                "Item price is invalid", "unit_price", "Contact support"));
        }
        
        // Validate availability
        if (!"AVAILABLE".equals(item.getAvailabilityStatus())) {
            messages.add(createValidationMessage("ERROR", "ITEM_NOT_AVAILABLE", 
                "Item is not available", "availability_status", "Remove item or find alternative"));
        }
        
        // Validate stock
        if (!item.isQuantityAvailable()) {
            messages.add(createValidationMessage("ERROR", "INSUFFICIENT_STOCK", 
                "Not enough stock available", "stock_quantity", "Reduce quantity or remove item"));
        }
        
        // Validate max quantity per order
        if (item.exceedsMaxQuantity()) {
            messages.add(createValidationMessage("ERROR", "EXCEEDS_MAX_QUANTITY", 
                "Quantity exceeds maximum allowed per order", "max_quantity_per_order", 
                "Reduce quantity to maximum allowed"));
        }
        
        // Check for price changes
        if (Boolean.TRUE.equals(item.getPriceChanged())) {
            messages.add(createValidationMessage("WARNING", "PRICE_CHANGED", 
                "Item price has changed since added to cart", "unit_price", "Review updated price"));
        }
        
        String validationStatus = messages.stream().anyMatch(msg -> "ERROR".equals(msg.getType())) ? "ERROR" :
                                 messages.stream().anyMatch(msg -> "WARNING".equals(msg.getType())) ? "WARNING" : "VALID";
        
        return CartValidationDto.ItemValidationDto.builder()
                .itemId(item.getId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .validationStatus(validationStatus)
                .messages(messages)
                .build();
    }

    /**
     * Validate item against current product data
     */
    public void validateCartItemAgainstCatalog(CartItem item) {
        try {
            // Get current product information
            ProductInfo productInfo = productCatalogClient.getProductInfo(item.getProductId());
            if (productInfo == null) {
                throw new RuntimeException("Product not found: " + item.getProductId());
            }

            // Update availability status
            item.setAvailabilityStatus(productInfo.getStockQuantity() > 0 ? "AVAILABLE" : "OUT_OF_STOCK");
            item.setStockQuantity(productInfo.getStockQuantity());

            // Check for price changes
            if (!item.getUnitPrice().equals(productInfo.getPrice())) {
                item.setPriceChanged(true);
                item.setLastPriceCheckAt(LocalDateTime.now());
            }

            // Validate business rules
            if (item.getQuantity() > MAX_QUANTITY_PER_ITEM) {
                throw new RuntimeException("Quantity exceeds maximum allowed: " + MAX_QUANTITY_PER_ITEM);
            }

            if (!item.isQuantityAvailable()) {
                throw new RuntimeException("Insufficient stock for product: " + item.getProductName());
            }

        } catch (Exception e) {
            log.error("Error validating cart item {}: {}", item.getId(), e.getMessage(), e);
            throw new RuntimeException("Item validation failed", e);
        }
    }

    // ==================== SPECIALIZED VALIDATIONS ====================

    /**
     * Validate pricing
     */
    private CartValidationDto.PricingValidationDto validatePricing(Cart cart) {
        List<CartValidationDto.ValidationMessageDto> messages = new ArrayList<>();
        List<CartValidationDto.PricingValidationDto.PriceChangeDto> priceChanges = new ArrayList<>();
        
        boolean isValid = true;
        int priceChangesCount = 0;
        
        // Check for price changes in items
        for (CartItem item : cart.getCartItems()) {
            if (Boolean.TRUE.equals(item.getPriceChanged())) {
                priceChangesCount++;
                // This would require getting current price from product service
                // For now, just mark as price change detected
                messages.add(createValidationMessage("WARNING", "PRICE_CHANGE_DETECTED", 
                    "Price changes detected in cart", "pricing", "Review updated prices"));
            }
        }
        
        return CartValidationDto.PricingValidationDto.builder()
                .isValid(isValid)
                .priceChangesCount(priceChangesCount)
                .priceChanges(priceChanges)
                .messages(messages)
                .build();
    }

    /**
     * Validate inventory
     */
    private CartValidationDto.InventoryValidationDto validateInventory(Cart cart) {
        List<CartValidationDto.ValidationMessageDto> messages = new ArrayList<>();
        List<String> outOfStockItems = new ArrayList<>();
        List<String> lowStockItems = new ArrayList<>();
        
        boolean isSufficient = true;
        
        for (CartItem item : cart.getCartItems()) {
            if (isItemOutOfStock(item)) {
                outOfStockItems.add(item.getProductName());
                isSufficient = false;
                messages.add(createValidationMessage("ERROR", "OUT_OF_STOCK",
                    "Item is out of stock: " + item.getProductName(), "stock", "Remove item"));
            } else if (isItemLowStock(item)) {
                lowStockItems.add(item.getProductName());
                messages.add(createValidationMessage("WARNING", "LOW_STOCK",
                    "Item is low in stock: " + item.getProductName(), "stock", "Consider purchasing soon"));
            }
        }
        
        return CartValidationDto.InventoryValidationDto.builder()
                .isSufficient(isSufficient)
                .outOfStockCount(outOfStockItems.size())
                .lowStockCount(lowStockItems.size())
                .outOfStockItems(outOfStockItems)
                .lowStockItems(lowStockItems)
                .messages(messages)
                .build();
    }

    /**
     * Validate shipping
     */
    private CartValidationDto.ShippingValidationDto validateShipping(Cart cart) {
        List<CartValidationDto.ValidationMessageDto> messages = new ArrayList<>();
        List<String> unshippableItems = new ArrayList<>();
        
        boolean isAvailable = true;
        
        // This would integrate with shipping service to validate shipping options
        // For now, assume all items are shippable
        
        return CartValidationDto.ShippingValidationDto.builder()
                .isAvailable(isAvailable)
                .unshippableCount(unshippableItems.size())
                .unshippableItems(unshippableItems)
                .messages(messages)
                .build();
    }

    /**
     * Validate coupon
     */
    private CartValidationDto.CouponValidationDto validateCoupon(Cart cart) {
        List<CartValidationDto.ValidationMessageDto> messages = new ArrayList<>();
        
        // This would integrate with coupon service
        // For now, assume coupon is valid if present
        boolean isValid = cart.getCouponCode() != null;
        BigDecimal discountAmount = cart.getDiscountAmount();
        
        if (!isValid) {
            messages.add(createValidationMessage("ERROR", "INVALID_COUPON", 
                "Coupon is not valid", "coupon_code", "Remove or replace coupon"));
        }
        
        return CartValidationDto.CouponValidationDto.builder()
                .couponCode(cart.getCouponCode())
                .isValid(isValid)
                .discountAmount(discountAmount)
                .messages(messages)
                .build();
    }

    // ==================== HELPER METHODS ====================

    private CartValidationDto.ValidationMessageDto createValidationMessage(
            String type, String code, String message, String field, String suggestedAction) {
        return CartValidationDto.ValidationMessageDto.builder()
                .type(type)
                .code(code)
                .message(message)
                .field(field)
                .suggestedAction(suggestedAction)
                .build();
    }

    private CartValidationDto createErrorValidation(String errorMessage) {
        return CartValidationDto.builder()
                .validationStatus("ERROR")
                .isValid(false)
                .hasErrors(true)
                .hasWarnings(false)
                .validationTimestamp(LocalDateTime.now())
                .generalMessages(List.of(createValidationMessage("ERROR", "VALIDATION_ERROR", 
                    errorMessage, null, "Contact support")))
                .errorCount(1)
                .warningCount(0)
                .validationScore(0)
                .build();
    }

    private Integer calculateValidationScore(boolean hasErrors, boolean hasWarnings) {
        if (hasErrors) {
            return 0;
        } else if (hasWarnings) {
            return 75;
        } else {
            return 100;
        }
    }

    /**
     * Check if item is out of stock
     */
    private boolean isItemOutOfStock(CartItem item) {
        return item.getStockQuantity() != null && item.getStockQuantity() <= 0;
    }

    /**
     * Check if item is low in stock
     */
    private boolean isItemLowStock(CartItem item) {
        return item.getStockQuantity() != null && item.getStockQuantity() <= 5 && item.getStockQuantity() > 0;
    }
}
