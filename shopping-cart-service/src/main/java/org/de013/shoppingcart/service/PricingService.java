package org.de013.shoppingcart.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.dto.response.PriceBreakdownDto;
import org.de013.shoppingcart.entity.Cart;
import org.de013.shoppingcart.entity.CartItem;
import org.de013.shoppingcart.entity.CartSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pricing Service
 * Handles complex pricing calculations including discounts, taxes, shipping, and promotions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PricingService {

    private final UserServiceClient userServiceClient;
    private final ProductCatalogClient productCatalogClient;

    @Value("${app.pricing.default-tax-rate:0.08}")
    private BigDecimal defaultTaxRate;

    @Value("${app.pricing.free-shipping-threshold:100.00}")
    private BigDecimal freeShippingThreshold;

    @Value("${app.pricing.standard-shipping-cost:9.99}")
    private BigDecimal standardShippingCost;

    // ==================== MAIN PRICING CALCULATIONS ====================

    /**
     * Calculate complete cart pricing
     */
    public CartSummary calculateCartPricing(Cart cart) {
        try {
            log.debug("Calculating pricing for cart: {}", cart.getId());
            
            CartSummary summary = CartSummary.builder()
                    .cart(cart)
                    .currency(cart.getCurrency())
                    .calculationTimestamp(LocalDateTime.now())
                    .build();
            
            // Calculate base amounts
            calculateBaseAmounts(cart, summary);
            
            // Apply discounts
            applyDiscounts(cart, summary);
            
            // Calculate tax
            calculateTax(cart, summary);
            
            // Calculate shipping
            calculateShipping(cart, summary);
            
            // Calculate loyalty benefits
            calculateLoyaltyBenefits(cart, summary);
            
            // Calculate final totals
            calculateFinalTotals(summary);
            
            log.debug("Completed pricing calculation for cart {}: total = {}", 
                     cart.getId(), summary.getTotalAmount());
            
            return summary;
            
        } catch (Exception e) {
            log.error("Error calculating cart pricing for cart {}: {}", cart.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to calculate cart pricing", e);
        }
    }

    /**
     * Get detailed price breakdown
     */
    public PriceBreakdownDto getDetailedPriceBreakdown(Cart cart) {
        try {
            CartSummary summary = calculateCartPricing(cart);
            
            return PriceBreakdownDto.builder()
                    .itemsSubtotal(summary.getSubtotal())
                    .productDiscounts(calculateProductDiscounts(cart))
                    .cartDiscounts(calculateCartDiscounts(cart))
                    .couponDiscounts(summary.getDiscountAmount())
                    .loyaltyDiscounts(summary.getLoyaltyDiscountAmount())
                    .subtotalAfterDiscounts(summary.getSubtotal().subtract(getTotalDiscounts(summary)))
                    .baseShippingCost(standardShippingCost)
                    .shippingDiscounts(calculateShippingDiscounts(summary))
                    .finalShippingCost(summary.getShippingCost())
                    .taxBreakdown(createTaxBreakdown(summary))
                    .totalTax(summary.getTaxAmount())
                    .feesBreakdown(createFeesBreakdown(summary))
                    .totalFees(calculateTotalFees(summary))
                    .giftWrapCosts(summary.getGiftWrapCost())
                    .insuranceCosts(summary.getInsuranceCost())
                    .finalTotal(summary.getTotalAmount())
                    .currency(summary.getCurrency())
                    .totalSavings(summary.getSavingsAmount())
                    .originalTotal(summary.getOriginalTotal())
                    .savingsPercentage(calculateSavingsPercentage(summary))
                    .build();
            
        } catch (Exception e) {
            log.error("Error creating price breakdown for cart {}: {}", cart.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to create price breakdown", e);
        }
    }

    // ==================== BASE CALCULATIONS ====================

    private void calculateBaseAmounts(Cart cart, CartSummary summary) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal originalTotal = BigDecimal.ZERO;
        int itemCount = 0;
        int totalQuantity = 0;
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal giftWrapCost = BigDecimal.ZERO;
        
        for (CartItem item : cart.getCartItems()) {
            subtotal = subtotal.add(item.getTotalPrice());
            
            // Calculate original total (before item-level discounts)
            BigDecimal itemOriginalTotal = item.getOriginalPrice() != null ? 
                item.getOriginalPrice().multiply(BigDecimal.valueOf(item.getQuantity())) :
                item.getTotalPrice();
            originalTotal = originalTotal.add(itemOriginalTotal);
            
            itemCount++;
            totalQuantity += item.getQuantity();
            
            if (item.getWeight() != null) {
                totalWeight = totalWeight.add(item.getWeight().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
            
            if (Boolean.TRUE.equals(item.getIsGift()) && item.getGiftWrapPrice() != null) {
                giftWrapCost = giftWrapCost.add(item.getGiftWrapPrice());
            }
        }
        
        summary.setSubtotal(subtotal);
        summary.setOriginalTotal(originalTotal);
        summary.setItemCount(itemCount);
        summary.setTotalQuantity(totalQuantity);
        summary.setTotalWeight(totalWeight);
        summary.setGiftWrapCost(giftWrapCost);
    }

    // ==================== DISCOUNT CALCULATIONS ====================

    private void applyDiscounts(Cart cart, CartSummary summary) {
        BigDecimal totalDiscount = BigDecimal.ZERO;
        
        // Apply coupon discount
        if (cart.getCouponCode() != null && cart.getDiscountAmount() != null) {
            summary.setDiscountCode(cart.getCouponCode());
            summary.setDiscountAmount(cart.getDiscountAmount());
            summary.setDiscountType("COUPON");
            totalDiscount = totalDiscount.add(cart.getDiscountAmount());
        }
        
        // Apply bulk discounts
        BigDecimal bulkDiscount = calculateBulkDiscount(cart);
        if (bulkDiscount.compareTo(BigDecimal.ZERO) > 0) {
            totalDiscount = totalDiscount.add(bulkDiscount);
            // Add to existing discount or create new one
            summary.setDiscountAmount(summary.getDiscountAmount().add(bulkDiscount));
        }
        
        // Calculate savings
        if (summary.getOriginalTotal() != null) {
            summary.setSavingsAmount(summary.getOriginalTotal().subtract(summary.getSubtotal()));
        }
    }

    private BigDecimal calculateProductDiscounts(Cart cart) {
        return cart.getCartItems().stream()
                .map(item -> item.getDiscountAmount() != null ? 
                     item.getDiscountAmount().multiply(BigDecimal.valueOf(item.getQuantity())) : 
                     BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateCartDiscounts(Cart cart) {
        // Calculate cart-level discounts (bulk, category, etc.)
        return calculateBulkDiscount(cart);
    }

    private BigDecimal calculateBulkDiscount(Cart cart) {
        // Example: 5% discount for orders over $200
        if (cart.getSubtotal().compareTo(new BigDecimal("200.00")) >= 0) {
            return cart.getSubtotal().multiply(new BigDecimal("0.05"));
        }
        return BigDecimal.ZERO;
    }

    // ==================== TAX CALCULATIONS ====================

    private void calculateTax(Cart cart, CartSummary summary) {
        // Get user's location for tax calculation
        BigDecimal taxRate = defaultTaxRate;
        
        if (cart.getUserId() != null) {
            UserServiceClient.ShippingAddress address = userServiceClient.getDefaultShippingAddress(cart.getUserId());
            if (address != null) {
                taxRate = getTaxRateForLocation(address.getState(), address.getCountry());
            }
        }
        
        BigDecimal taxableAmount = summary.getSubtotal().subtract(
            summary.getDiscountAmount() != null ? summary.getDiscountAmount() : BigDecimal.ZERO);
        
        BigDecimal taxAmount = taxableAmount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        
        summary.setTaxRate(taxRate);
        summary.setTaxAmount(taxAmount);
    }

    private BigDecimal getTaxRateForLocation(String state, String country) {
        // This would integrate with tax service or lookup table
        // For now, return default rate
        return defaultTaxRate;
    }

    // ==================== SHIPPING CALCULATIONS ====================

    private void calculateShipping(Cart cart, CartSummary summary) {
        // Check for free shipping eligibility
        boolean isFreeShippingEligible = summary.getSubtotal().compareTo(freeShippingThreshold) >= 0;
        
        summary.setIsFreeShippingEligible(isFreeShippingEligible);
        summary.setFreeShippingThreshold(freeShippingThreshold);
        
        if (isFreeShippingEligible) {
            summary.setShippingCost(BigDecimal.ZERO);
            summary.setAmountNeededForFreeShipping(BigDecimal.ZERO);
        } else {
            // Calculate shipping based on weight and destination
            BigDecimal shippingCost = calculateShippingCost(cart, summary);
            summary.setShippingCost(shippingCost);
            summary.setAmountNeededForFreeShipping(freeShippingThreshold.subtract(summary.getSubtotal()));
        }
        
        summary.setShippingMethod("Standard Shipping");
        summary.setShippingEstimatedDays(5);
    }

    private BigDecimal calculateShippingCost(Cart cart, CartSummary summary) {
        // Base shipping cost
        BigDecimal shippingCost = standardShippingCost;
        
        // Add weight-based charges
        if (summary.getTotalWeight() != null && summary.getTotalWeight().compareTo(new BigDecimal("5.0")) > 0) {
            BigDecimal extraWeight = summary.getTotalWeight().subtract(new BigDecimal("5.0"));
            BigDecimal extraCost = extraWeight.multiply(new BigDecimal("2.00"));
            shippingCost = shippingCost.add(extraCost);
        }
        
        return shippingCost;
    }

    private BigDecimal calculateShippingDiscounts(CartSummary summary) {
        if (Boolean.TRUE.equals(summary.getIsFreeShippingEligible())) {
            return standardShippingCost;
        }
        return BigDecimal.ZERO;
    }

    // ==================== LOYALTY CALCULATIONS ====================

    private void calculateLoyaltyBenefits(Cart cart, CartSummary summary) {
        if (cart.getUserId() == null) {
            return;
        }
        
        UserServiceClient.LoyaltyInfo loyaltyInfo = userServiceClient.getUserLoyaltyInfo(cart.getUserId());
        if (loyaltyInfo == null) {
            return;
        }
        
        // Calculate points earned
        int pointsEarned = summary.getSubtotal().intValue(); // 1 point per dollar
        summary.setLoyaltyPointsEarned(pointsEarned);
        
        // Apply loyalty discount if applicable
        if (loyaltyInfo.getDiscountPercentage() != null && loyaltyInfo.getDiscountPercentage() > 0) {
            BigDecimal loyaltyDiscount = summary.getSubtotal()
                    .multiply(BigDecimal.valueOf(loyaltyInfo.getDiscountPercentage() / 100))
                    .setScale(2, RoundingMode.HALF_UP);
            summary.setLoyaltyDiscountAmount(loyaltyDiscount);
        }
    }

    // ==================== FINAL CALCULATIONS ====================

    private void calculateFinalTotals(CartSummary summary) {
        summary.calculateTotal();
        summary.calculateSavings();
        summary.checkFreeShippingEligibility();
    }

    private BigDecimal getTotalDiscounts(CartSummary summary) {
        BigDecimal total = BigDecimal.ZERO;
        
        if (summary.getDiscountAmount() != null) {
            total = total.add(summary.getDiscountAmount());
        }
        
        if (summary.getLoyaltyDiscountAmount() != null) {
            total = total.add(summary.getLoyaltyDiscountAmount());
        }
        
        return total;
    }

    private BigDecimal calculateTotalFees(CartSummary summary) {
        BigDecimal total = BigDecimal.ZERO;
        
        if (summary.getHandlingFee() != null) {
            total = total.add(summary.getHandlingFee());
        }
        
        if (summary.getInsuranceCost() != null) {
            total = total.add(summary.getInsuranceCost());
        }
        
        if (summary.getGiftWrapCost() != null) {
            total = total.add(summary.getGiftWrapCost());
        }
        
        return total;
    }

    private BigDecimal calculateSavingsPercentage(CartSummary summary) {
        if (summary.getOriginalTotal() != null && summary.getOriginalTotal().compareTo(BigDecimal.ZERO) > 0) {
            return summary.getSavingsAmount()
                    .divide(summary.getOriginalTotal(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        return BigDecimal.ZERO;
    }

    // ==================== HELPER METHODS ====================

    private List<PriceBreakdownDto.TaxLineDto> createTaxBreakdown(CartSummary summary) {
        List<PriceBreakdownDto.TaxLineDto> taxBreakdown = new ArrayList<>();
        
        if (summary.getTaxAmount() != null && summary.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
            taxBreakdown.add(PriceBreakdownDto.TaxLineDto.builder()
                    .taxType("Sales Tax")
                    .jurisdiction("Default")
                    .taxRate(summary.getTaxRate())
                    .taxableAmount(summary.getSubtotal())
                    .taxAmount(summary.getTaxAmount())
                    .description("Standard sales tax")
                    .build());
        }
        
        return taxBreakdown;
    }

    private List<PriceBreakdownDto.FeeLineDto> createFeesBreakdown(CartSummary summary) {
        List<PriceBreakdownDto.FeeLineDto> feesBreakdown = new ArrayList<>();
        
        if (summary.getHandlingFee() != null && summary.getHandlingFee().compareTo(BigDecimal.ZERO) > 0) {
            feesBreakdown.add(PriceBreakdownDto.FeeLineDto.builder()
                    .feeType("HANDLING")
                    .feeName("Handling Fee")
                    .feeAmount(summary.getHandlingFee())
                    .description("Order processing and handling")
                    .isOptional(false)
                    .build());
        }
        
        if (summary.getInsuranceCost() != null && summary.getInsuranceCost().compareTo(BigDecimal.ZERO) > 0) {
            feesBreakdown.add(PriceBreakdownDto.FeeLineDto.builder()
                    .feeType("INSURANCE")
                    .feeName("Shipping Insurance")
                    .feeAmount(summary.getInsuranceCost())
                    .description("Package insurance coverage")
                    .isOptional(true)
                    .build());
        }
        
        return feesBreakdown;
    }
}
