package org.de013.shoppingcart.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.client.ProductCatalogFeignClient;
import org.de013.shoppingcart.client.UserServiceFeignClient;
import org.de013.shoppingcart.entity.Cart;
import org.de013.shoppingcart.entity.CartItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dynamic Pricing Calculation Service
 * Handles dynamic pricing, bulk discounts, loyalty pricing, and promotional calculations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicPricingService {

    private final ProductCatalogFeignClient productCatalogFeignClient;
    private final UserServiceFeignClient userServiceFeignClient;
    private final TaxCalculationService taxCalculationService;
    private final ShippingCostService shippingCostService;

    // Configuration values
    @Value("${shopping-cart.pricing.bulk-discount-threshold:5}")
    private int bulkDiscountThreshold;

    @Value("${shopping-cart.pricing.bulk-discount-percent:5.0}")
    private double bulkDiscountPercent;

    @Value("${shopping-cart.pricing.loyalty-discount-percent:10.0}")
    private double loyaltyDiscountPercent;

    @Value("${shopping-cart.pricing.free-shipping-threshold:50.00}")
    private BigDecimal freeShippingThreshold;

    @Value("${shopping-cart.pricing.premium-member-discount:15.0}")
    private double premiumMemberDiscount;

    // ==================== DYNAMIC PRICING CALCULATION ====================

    /**
     * Calculate dynamic pricing for entire cart
     */
    public Map<String, Object> calculateCartPricing(Cart cart) {
        try {
            log.debug("Calculating dynamic pricing for cart: {}", cart.getId());
            
            Map<String, Object> result = new HashMap<>();
            result.put("cartId", cart.getId());
            result.put("timestamp", LocalDateTime.now());
            
            // Get user information for personalized pricing
            Map<String, Object> userInfo = getUserPricingInfo(cart.getUserId());
            
            // Calculate base pricing
            Map<String, Object> basePricing = calculateBasePricing(cart);
            result.put("basePricing", basePricing);
            
            // Apply bulk discounts
            Map<String, Object> bulkDiscounts = calculateBulkDiscounts(cart);
            result.put("bulkDiscounts", bulkDiscounts);
            
            // Apply loyalty discounts
            Map<String, Object> loyaltyDiscounts = calculateLoyaltyDiscounts(cart, userInfo);
            result.put("loyaltyDiscounts", loyaltyDiscounts);
            
            // Apply promotional discounts
            Map<String, Object> promotionalDiscounts = calculatePromotionalDiscounts(cart);
            result.put("promotionalDiscounts", promotionalDiscounts);
            
            // Calculate tax
            Map<String, Object> taxCalculation = taxCalculationService.calculateCartTax(cart);
            result.put("taxCalculation", taxCalculation);

            // Calculate shipping
            Map<String, Object> shippingCalculation = shippingCostService.calculateShippingCosts(cart);
            result.put("shippingCalculation", shippingCalculation);

            // Calculate final pricing including tax and shipping
            Map<String, Object> finalPricing = calculateFinalPricingWithTaxAndShipping(
                basePricing, bulkDiscounts, loyaltyDiscounts, promotionalDiscounts,
                taxCalculation, shippingCalculation);
            result.put("finalPricing", finalPricing);

            // Calculate savings
            BigDecimal originalTotal = (BigDecimal) basePricing.get("total");
            BigDecimal finalTotal = (BigDecimal) finalPricing.get("grandTotal");
            BigDecimal totalSavings = originalTotal.subtract((BigDecimal) finalPricing.get("subtotalAfterDiscounts"));

            result.put("totalSavings", totalSavings);
            result.put("savingsPercent", calculateSavingsPercent(originalTotal, totalSavings));
            
            return result;
            
        } catch (Exception e) {
            log.error("Error calculating dynamic pricing for cart {}: {}", cart.getId(), e.getMessage(), e);
            
            return Map.of(
                "cartId", cart.getId(),
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
        }
    }

    /**
     * Calculate base pricing without discounts
     */
    private Map<String, Object> calculateBasePricing(Cart cart) {
        Map<String, Object> result = new HashMap<>();
        
        BigDecimal subtotal = BigDecimal.ZERO;
        int totalQuantity = 0;
        
        for (CartItem item : cart.getCartItems()) {
            BigDecimal itemTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            subtotal = subtotal.add(itemTotal);
            totalQuantity += item.getQuantity();
        }
        
        result.put("subtotal", subtotal);
        result.put("totalQuantity", totalQuantity);
        result.put("itemCount", cart.getCartItems().size());
        result.put("total", subtotal);
        
        return result;
    }

    // ==================== BULK DISCOUNT CALCULATION ====================

    /**
     * Calculate bulk discounts based on quantity and value
     */
    private Map<String, Object> calculateBulkDiscounts(Cart cart) {
        Map<String, Object> result = new HashMap<>();
        result.put("applied", false);
        result.put("discountAmount", BigDecimal.ZERO);
        result.put("discountPercent", 0.0);
        
        try {
            int totalQuantity = cart.getCartItems().stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
            
            // Check if eligible for bulk discount
            if (totalQuantity >= bulkDiscountThreshold) {
                BigDecimal subtotal = cart.getCartItems().stream()
                    .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                // Calculate tiered bulk discount
                double discountPercent = calculateTieredBulkDiscount(totalQuantity);
                BigDecimal discountAmount = subtotal.multiply(BigDecimal.valueOf(discountPercent / 100))
                    .setScale(2, RoundingMode.HALF_UP);
                
                result.put("applied", true);
                result.put("discountAmount", discountAmount);
                result.put("discountPercent", discountPercent);
                result.put("qualifyingQuantity", totalQuantity);
                result.put("threshold", bulkDiscountThreshold);
                
                log.debug("Bulk discount applied: {}% on {} items = ${}", 
                         discountPercent, totalQuantity, discountAmount);
            }
            
        } catch (Exception e) {
            log.error("Error calculating bulk discounts: {}", e.getMessage());
        }
        
        return result;
    }

    /**
     * Calculate tiered bulk discount percentage
     */
    private double calculateTieredBulkDiscount(int quantity) {
        if (quantity >= 20) {
            return 15.0; // 15% for 20+ items
        } else if (quantity >= 10) {
            return 10.0; // 10% for 10-19 items
        } else if (quantity >= 5) {
            return 5.0;  // 5% for 5-9 items
        }
        return 0.0;
    }

    // ==================== LOYALTY DISCOUNT CALCULATION ====================

    /**
     * Calculate loyalty-based discounts
     */
    private Map<String, Object> calculateLoyaltyDiscounts(Cart cart, Map<String, Object> userInfo) {
        Map<String, Object> result = new HashMap<>();
        result.put("applied", false);
        result.put("discountAmount", BigDecimal.ZERO);
        result.put("discountPercent", 0.0);
        
        try {
            if (cart.getUserId() == null || Boolean.TRUE.equals(userInfo.get("fallback"))) {
                return result;
            }
            
            // Get user loyalty information
            Map<String, Object> loyaltyInfo = userServiceFeignClient.getUserLoyalty(cart.getUserId());
            
            if (Boolean.TRUE.equals(loyaltyInfo.get("fallback"))) {
                return result;
            }
            
            String membershipLevel = (String) loyaltyInfo.getOrDefault("level", "BASIC");
            Integer loyaltyPoints = (Integer) loyaltyInfo.getOrDefault("points", 0);
            
            // Calculate loyalty discount based on membership level
            double discountPercent = calculateLoyaltyDiscountPercent(membershipLevel, loyaltyPoints);
            
            if (discountPercent > 0) {
                BigDecimal subtotal = cart.getCartItems().stream()
                    .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                BigDecimal discountAmount = subtotal.multiply(BigDecimal.valueOf(discountPercent / 100))
                    .setScale(2, RoundingMode.HALF_UP);
                
                result.put("applied", true);
                result.put("discountAmount", discountAmount);
                result.put("discountPercent", discountPercent);
                result.put("membershipLevel", membershipLevel);
                result.put("loyaltyPoints", loyaltyPoints);
                
                log.debug("Loyalty discount applied: {}% for {} member = ${}", 
                         discountPercent, membershipLevel, discountAmount);
            }
            
        } catch (Exception e) {
            log.error("Error calculating loyalty discounts: {}", e.getMessage());
        }
        
        return result;
    }

    /**
     * Calculate loyalty discount percentage based on membership level
     */
    private double calculateLoyaltyDiscountPercent(String membershipLevel, int loyaltyPoints) {
        return switch (membershipLevel.toUpperCase()) {
            case "PLATINUM" -> 20.0;
            case "GOLD" -> 15.0;
            case "SILVER" -> 10.0;
            case "BRONZE" -> 5.0;
            default -> {
                // Points-based discount for basic members
                if (loyaltyPoints >= 1000) {
                    yield 5.0;
                } else if (loyaltyPoints >= 500) {
                    yield 3.0;
                } else if (loyaltyPoints >= 100) {
                    yield 1.0;
                }
                yield 0.0;
            }
        };
    }

    // ==================== PROMOTIONAL DISCOUNT CALCULATION ====================

    /**
     * Calculate promotional discounts from product catalog
     */
    @Cacheable(value = "promotional-discounts", key = "#cart.id")
    private Map<String, Object> calculatePromotionalDiscounts(Cart cart) {
        Map<String, Object> result = new HashMap<>();
        result.put("applied", false);
        result.put("discountAmount", BigDecimal.ZERO);
        result.put("promotions", List.of());
        
        try {
            BigDecimal totalPromotionalDiscount = BigDecimal.ZERO;
            List<Map<String, Object>> appliedPromotions = new java.util.ArrayList<>();
            
            // Check promotions for each cart item
            for (CartItem item : cart.getCartItems()) {
                try {
                    List<Map<String, Object>> itemPromotions = 
                        productCatalogFeignClient.getActivePromotions(item.getProductId());
                    
                    for (Map<String, Object> promotion : itemPromotions) {
                        Map<String, Object> discountResult = productCatalogFeignClient.calculateDiscount(
                            item.getProductId(), 
                            Map.of(
                                "quantity", item.getQuantity(),
                                "unitPrice", item.getUnitPrice(),
                                "promotionId", promotion.get("id")
                            )
                        );
                        
                        if (!Boolean.TRUE.equals(discountResult.get("fallback"))) {
                            BigDecimal discountAmount = new BigDecimal(
                                discountResult.getOrDefault("discountAmount", "0").toString());
                            
                            if (discountAmount.compareTo(BigDecimal.ZERO) > 0) {
                                totalPromotionalDiscount = totalPromotionalDiscount.add(discountAmount);
                                
                                Map<String, Object> appliedPromotion = new HashMap<>();
                                appliedPromotion.put("productId", item.getProductId());
                                appliedPromotion.put("promotionId", promotion.get("id"));
                                appliedPromotion.put("discountAmount", discountAmount);
                                appliedPromotion.put("description", promotion.get("description"));
                                appliedPromotions.add(appliedPromotion);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error calculating promotions for item {}: {}", item.getId(), e.getMessage());
                }
            }
            
            if (totalPromotionalDiscount.compareTo(BigDecimal.ZERO) > 0) {
                result.put("applied", true);
                result.put("discountAmount", totalPromotionalDiscount);
                result.put("promotions", appliedPromotions);
                
                log.debug("Promotional discounts applied: ${} from {} promotions", 
                         totalPromotionalDiscount, appliedPromotions.size());
            }
            
        } catch (Exception e) {
            log.error("Error calculating promotional discounts: {}", e.getMessage());
        }
        
        return result;
    }

    // ==================== FINAL PRICING CALCULATION ====================

    /**
     * Calculate final pricing combining all discounts
     */
    private Map<String, Object> calculateFinalPricing(
            Map<String, Object> basePricing,
            Map<String, Object> bulkDiscounts,
            Map<String, Object> loyaltyDiscounts,
            Map<String, Object> promotionalDiscounts) {

        Map<String, Object> result = new HashMap<>();

        BigDecimal subtotal = (BigDecimal) basePricing.get("subtotal");
        BigDecimal totalDiscounts = BigDecimal.ZERO;

        // Sum all discounts
        totalDiscounts = totalDiscounts.add((BigDecimal) bulkDiscounts.get("discountAmount"));
        totalDiscounts = totalDiscounts.add((BigDecimal) loyaltyDiscounts.get("discountAmount"));
        totalDiscounts = totalDiscounts.add((BigDecimal) promotionalDiscounts.get("discountAmount"));

        BigDecimal finalTotal = subtotal.subtract(totalDiscounts);

        // Ensure final total is not negative
        if (finalTotal.compareTo(BigDecimal.ZERO) < 0) {
            finalTotal = BigDecimal.ZERO;
        }

        result.put("subtotal", subtotal);
        result.put("totalDiscounts", totalDiscounts);
        result.put("total", finalTotal);
        result.put("discountBreakdown", Map.of(
            "bulk", bulkDiscounts.get("discountAmount"),
            "loyalty", loyaltyDiscounts.get("discountAmount"),
            "promotional", promotionalDiscounts.get("discountAmount")
        ));

        return result;
    }

    /**
     * Calculate final pricing including tax and shipping
     */
    private Map<String, Object> calculateFinalPricingWithTaxAndShipping(
            Map<String, Object> basePricing,
            Map<String, Object> bulkDiscounts,
            Map<String, Object> loyaltyDiscounts,
            Map<String, Object> promotionalDiscounts,
            Map<String, Object> taxCalculation,
            Map<String, Object> shippingCalculation) {

        Map<String, Object> result = new HashMap<>();

        try {
            BigDecimal subtotal = (BigDecimal) basePricing.get("subtotal");
            BigDecimal totalDiscounts = BigDecimal.ZERO;

            // Sum all discounts
            totalDiscounts = totalDiscounts.add((BigDecimal) bulkDiscounts.get("discountAmount"));
            totalDiscounts = totalDiscounts.add((BigDecimal) loyaltyDiscounts.get("discountAmount"));
            totalDiscounts = totalDiscounts.add((BigDecimal) promotionalDiscounts.get("discountAmount"));

            BigDecimal subtotalAfterDiscounts = subtotal.subtract(totalDiscounts);

            // Ensure subtotal is not negative
            if (subtotalAfterDiscounts.compareTo(BigDecimal.ZERO) < 0) {
                subtotalAfterDiscounts = BigDecimal.ZERO;
            }

            // Get tax amount
            BigDecimal taxAmount = BigDecimal.ZERO;
            if (taxCalculation.containsKey("finalTax")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> finalTax = (Map<String, Object>) taxCalculation.get("finalTax");
                taxAmount = (BigDecimal) finalTax.getOrDefault("totalTaxAmount", BigDecimal.ZERO);
            }

            // Get shipping amount (use recommended option)
            BigDecimal shippingAmount = BigDecimal.ZERO;
            if (shippingCalculation.containsKey("recommendedOption")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> recommendedOption = (Map<String, Object>) shippingCalculation.get("recommendedOption");
                shippingAmount = (BigDecimal) recommendedOption.getOrDefault("cost", BigDecimal.ZERO);
            }

            // Calculate grand total
            BigDecimal grandTotal = subtotalAfterDiscounts.add(taxAmount).add(shippingAmount);

            result.put("subtotal", subtotal);
            result.put("totalDiscounts", totalDiscounts);
            result.put("subtotalAfterDiscounts", subtotalAfterDiscounts);
            result.put("taxAmount", taxAmount);
            result.put("shippingAmount", shippingAmount);
            result.put("grandTotal", grandTotal);

            result.put("discountBreakdown", Map.of(
                "bulk", bulkDiscounts.get("discountAmount"),
                "loyalty", loyaltyDiscounts.get("discountAmount"),
                "promotional", promotionalDiscounts.get("discountAmount")
            ));

            // Calculate effective rates
            if (subtotal.compareTo(BigDecimal.ZERO) > 0) {
                double discountRate = totalDiscounts.divide(subtotal, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
                double taxRate = taxAmount.divide(subtotalAfterDiscounts, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();

                result.put("effectiveDiscountRate", discountRate);
                result.put("effectiveTaxRate", taxRate);
            }

        } catch (Exception e) {
            log.error("Error calculating final pricing with tax and shipping: {}", e.getMessage());
            result.put("error", e.getMessage());
        }

        return result;
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Get user pricing information
     */
    private Map<String, Object> getUserPricingInfo(String userId) {
        if (userId == null) {
            return Map.of("guest", true);
        }
        
        try {
            return userServiceFeignClient.getUserById(userId);
        } catch (Exception e) {
            log.warn("Error getting user pricing info for {}: {}", userId, e.getMessage());
            return Map.of("fallback", true);
        }
    }

    /**
     * Calculate savings percentage
     */
    private double calculateSavingsPercent(BigDecimal originalTotal, BigDecimal savings) {
        if (originalTotal.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        
        return savings.divide(originalTotal, 4, RoundingMode.HALF_UP)
                     .multiply(BigDecimal.valueOf(100))
                     .doubleValue();
    }

    /**
     * Check if cart qualifies for free shipping
     */
    public boolean qualifiesForFreeShipping(BigDecimal cartTotal) {
        return cartTotal.compareTo(freeShippingThreshold) >= 0;
    }

    /**
     * Calculate amount needed for free shipping
     */
    public BigDecimal getAmountNeededForFreeShipping(BigDecimal cartTotal) {
        if (qualifiesForFreeShipping(cartTotal)) {
            return BigDecimal.ZERO;
        }
        
        return freeShippingThreshold.subtract(cartTotal);
    }
}
