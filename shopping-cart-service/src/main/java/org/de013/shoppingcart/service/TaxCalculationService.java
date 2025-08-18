package org.de013.shoppingcart.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Map;

/**
 * Tax Calculation Service
 * Handles comprehensive tax calculations including sales tax, VAT, and regional taxes
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaxCalculationService {

    private final UserServiceFeignClient userServiceFeignClient;

    // Configuration values
    @Value("${shopping-cart.tax.default-rate:0.08}")
    private double defaultTaxRate;

    @Value("${shopping-cart.tax.vat-rate:0.20}")
    private double vatRate;

    @Value("${shopping-cart.tax.luxury-tax-threshold:1000.00}")
    private BigDecimal luxuryTaxThreshold;

    @Value("${shopping-cart.tax.luxury-tax-rate:0.05}")
    private double luxuryTaxRate;

    @Value("${shopping-cart.tax.digital-goods-rate:0.06}")
    private double digitalGoodsRate;

    @Value("${shopping-cart.tax.exempt-threshold:0.00}")
    private BigDecimal taxExemptThreshold;

    // ==================== TAX CALCULATION ====================

    /**
     * Calculate comprehensive tax for entire cart
     */
    public Map<String, Object> calculateCartTax(Cart cart) {
        try {
            log.debug("Calculating tax for cart: {}", cart.getId());
            
            Map<String, Object> result = new HashMap<>();
            result.put("cartId", cart.getId());
            result.put("timestamp", LocalDateTime.now());
            
            // Get user location for tax jurisdiction
            Map<String, Object> userLocation = getUserTaxLocation(cart.getUserId());
            
            // Calculate base tax
            Map<String, Object> baseTax = calculateBaseTax(cart, userLocation);
            result.put("baseTax", baseTax);
            
            // Calculate special taxes
            Map<String, Object> specialTaxes = calculateSpecialTaxes(cart, userLocation);
            result.put("specialTaxes", specialTaxes);
            
            // Calculate final tax amounts
            Map<String, Object> finalTax = calculateFinalTax(baseTax, specialTaxes);
            result.put("finalTax", finalTax);
            
            // Tax breakdown by item
            Map<String, Object> itemTaxBreakdown = calculateItemTaxBreakdown(cart, userLocation);
            result.put("itemTaxBreakdown", itemTaxBreakdown);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error calculating tax for cart {}: {}", cart.getId(), e.getMessage(), e);
            
            return Map.of(
                "cartId", cart.getId(),
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
        }
    }

    /**
     * Calculate base tax (sales tax/VAT)
     */
    private Map<String, Object> calculateBaseTax(Cart cart, Map<String, Object> userLocation) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            BigDecimal subtotal = cart.getCartItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Determine tax rate based on location
            double taxRate = determineTaxRate(userLocation);
            
            // Check for tax exemptions
            boolean isExempt = checkTaxExemption(cart, userLocation);
            
            BigDecimal taxAmount = BigDecimal.ZERO;
            if (!isExempt && subtotal.compareTo(taxExemptThreshold) > 0) {
                taxAmount = subtotal.multiply(BigDecimal.valueOf(taxRate))
                    .setScale(2, RoundingMode.HALF_UP);
            }
            
            result.put("subtotal", subtotal);
            result.put("taxRate", taxRate);
            result.put("taxAmount", taxAmount);
            result.put("isExempt", isExempt);
            result.put("jurisdiction", userLocation.getOrDefault("jurisdiction", "DEFAULT"));
            
        } catch (Exception e) {
            log.error("Error calculating base tax: {}", e.getMessage());
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Calculate special taxes (luxury, digital goods, etc.)
     */
    private Map<String, Object> calculateSpecialTaxes(Cart cart, Map<String, Object> userLocation) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            BigDecimal luxuryTaxAmount = BigDecimal.ZERO;
            BigDecimal digitalGoodsTaxAmount = BigDecimal.ZERO;
            BigDecimal environmentalTaxAmount = BigDecimal.ZERO;
            
            for (CartItem item : cart.getCartItems()) {
                BigDecimal itemTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                
                // Luxury tax for high-value items
                if (item.getUnitPrice().compareTo(luxuryTaxThreshold) > 0) {
                    BigDecimal luxuryTax = itemTotal.multiply(BigDecimal.valueOf(luxuryTaxRate))
                        .setScale(2, RoundingMode.HALF_UP);
                    luxuryTaxAmount = luxuryTaxAmount.add(luxuryTax);
                }
                
                // Digital goods tax
                if (isDigitalGood(item)) {
                    BigDecimal digitalTax = itemTotal.multiply(BigDecimal.valueOf(digitalGoodsRate))
                        .setScale(2, RoundingMode.HALF_UP);
                    digitalGoodsTaxAmount = digitalGoodsTaxAmount.add(digitalTax);
                }
                
                // Environmental tax for certain categories
                if (requiresEnvironmentalTax(item)) {
                    BigDecimal envTax = calculateEnvironmentalTax(item);
                    environmentalTaxAmount = environmentalTaxAmount.add(envTax);
                }
            }
            
            result.put("luxuryTax", Map.of(
                "amount", luxuryTaxAmount,
                "rate", luxuryTaxRate,
                "threshold", luxuryTaxThreshold
            ));
            
            result.put("digitalGoodsTax", Map.of(
                "amount", digitalGoodsTaxAmount,
                "rate", digitalGoodsRate
            ));
            
            result.put("environmentalTax", Map.of(
                "amount", environmentalTaxAmount
            ));
            
            BigDecimal totalSpecialTax = luxuryTaxAmount.add(digitalGoodsTaxAmount).add(environmentalTaxAmount);
            result.put("totalSpecialTax", totalSpecialTax);
            
        } catch (Exception e) {
            log.error("Error calculating special taxes: {}", e.getMessage());
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Calculate final tax combining all tax types
     */
    private Map<String, Object> calculateFinalTax(Map<String, Object> baseTax, Map<String, Object> specialTaxes) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            BigDecimal baseTaxAmount = (BigDecimal) baseTax.getOrDefault("taxAmount", BigDecimal.ZERO);
            BigDecimal specialTaxAmount = (BigDecimal) specialTaxes.getOrDefault("totalSpecialTax", BigDecimal.ZERO);
            
            BigDecimal totalTax = baseTaxAmount.add(specialTaxAmount);
            
            result.put("baseTaxAmount", baseTaxAmount);
            result.put("specialTaxAmount", specialTaxAmount);
            result.put("totalTaxAmount", totalTax);
            result.put("effectiveTaxRate", calculateEffectiveTaxRate(baseTax, totalTax));
            
        } catch (Exception e) {
            log.error("Error calculating final tax: {}", e.getMessage());
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Calculate tax breakdown by item
     */
    private Map<String, Object> calculateItemTaxBreakdown(Cart cart, Map<String, Object> userLocation) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            double taxRate = determineTaxRate(userLocation);
            Map<Long, Map<String, Object>> itemTaxes = new HashMap<>();
            
            for (CartItem item : cart.getCartItems()) {
                Map<String, Object> itemTax = new HashMap<>();
                
                BigDecimal itemTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                BigDecimal baseTax = itemTotal.multiply(BigDecimal.valueOf(taxRate))
                    .setScale(2, RoundingMode.HALF_UP);
                
                itemTax.put("itemId", item.getId());
                itemTax.put("productId", item.getProductId());
                itemTax.put("itemTotal", itemTotal);
                itemTax.put("baseTax", baseTax);
                itemTax.put("taxRate", taxRate);
                
                // Add special taxes for this item
                if (item.getUnitPrice().compareTo(luxuryTaxThreshold) > 0) {
                    BigDecimal luxuryTax = itemTotal.multiply(BigDecimal.valueOf(luxuryTaxRate))
                        .setScale(2, RoundingMode.HALF_UP);
                    itemTax.put("luxuryTax", luxuryTax);
                }
                
                if (isDigitalGood(item)) {
                    BigDecimal digitalTax = itemTotal.multiply(BigDecimal.valueOf(digitalGoodsRate))
                        .setScale(2, RoundingMode.HALF_UP);
                    itemTax.put("digitalGoodsTax", digitalTax);
                }
                
                BigDecimal totalItemTax = baseTax;
                if (itemTax.containsKey("luxuryTax")) {
                    totalItemTax = totalItemTax.add((BigDecimal) itemTax.get("luxuryTax"));
                }
                if (itemTax.containsKey("digitalGoodsTax")) {
                    totalItemTax = totalItemTax.add((BigDecimal) itemTax.get("digitalGoodsTax"));
                }
                
                itemTax.put("totalItemTax", totalItemTax);
                itemTaxes.put(item.getId(), itemTax);
            }
            
            result.put("itemTaxes", itemTaxes);
            
        } catch (Exception e) {
            log.error("Error calculating item tax breakdown: {}", e.getMessage());
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    // ==================== TAX RATE DETERMINATION ====================

    /**
     * Determine tax rate based on user location
     */
    @Cacheable(value = "tax-rates", key = "#userLocation.get('jurisdiction')")
    private double determineTaxRate(Map<String, Object> userLocation) {
        try {
            String jurisdiction = (String) userLocation.getOrDefault("jurisdiction", "DEFAULT");
            String country = (String) userLocation.getOrDefault("country", "US");
            String state = (String) userLocation.getOrDefault("state", "");
            
            // Tax rate lookup by jurisdiction
            return switch (jurisdiction.toUpperCase()) {
                case "US_CA" -> 0.0975; // California
                case "US_NY" -> 0.08;   // New York
                case "US_TX" -> 0.0625; // Texas
                case "US_FL" -> 0.06;   // Florida
                case "EU" -> vatRate;   // European VAT
                case "UK" -> 0.20;      // UK VAT
                case "CA" -> 0.13;      // Canada GST+PST
                default -> defaultTaxRate;
            };
            
        } catch (Exception e) {
            log.warn("Error determining tax rate, using default: {}", e.getMessage());
            return defaultTaxRate;
        }
    }

    /**
     * Check for tax exemptions
     */
    private boolean checkTaxExemption(Cart cart, Map<String, Object> userLocation) {
        try {
            // Check user tax exemption status
            if (cart.getUserId() != null) {
                Map<String, Object> userInfo = userServiceFeignClient.getUserById(cart.getUserId());
                if (!Boolean.TRUE.equals(userInfo.get("fallback"))) {
                    Boolean isTaxExempt = (Boolean) userInfo.getOrDefault("taxExempt", false);
                    if (Boolean.TRUE.equals(isTaxExempt)) {
                        return true;
                    }
                }
            }
            
            // Check jurisdiction-specific exemptions
            String jurisdiction = (String) userLocation.getOrDefault("jurisdiction", "DEFAULT");
            if ("TAX_FREE_ZONE".equals(jurisdiction)) {
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.warn("Error checking tax exemption: {}", e.getMessage());
            return false;
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Get user tax location information
     */
    private Map<String, Object> getUserTaxLocation(String userId) {
        try {
            if (userId == null) {
                return Map.of("jurisdiction", "DEFAULT", "country", "US");
            }
            
            Map<String, Object> userAddress = userServiceFeignClient.getDefaultShippingAddress(userId);
            
            if (Boolean.TRUE.equals(userAddress.get("fallback")) || !Boolean.TRUE.equals(userAddress.get("hasAddress"))) {
                return Map.of("jurisdiction", "DEFAULT", "country", "US");
            }
            
            // Extract location information from address
            String country = (String) userAddress.getOrDefault("country", "US");
            String state = (String) userAddress.getOrDefault("state", "");
            String jurisdiction = determineJurisdiction(country, state);
            
            return Map.of(
                "jurisdiction", jurisdiction,
                "country", country,
                "state", state
            );
            
        } catch (Exception e) {
            log.warn("Error getting user tax location: {}", e.getMessage());
            return Map.of("jurisdiction", "DEFAULT", "country", "US");
        }
    }

    /**
     * Determine tax jurisdiction from country and state
     */
    private String determineJurisdiction(String country, String state) {
        if ("US".equals(country)) {
            return "US_" + state.toUpperCase();
        } else if (isEUCountry(country)) {
            return "EU";
        } else if ("GB".equals(country) || "UK".equals(country)) {
            return "UK";
        } else if ("CA".equals(country)) {
            return "CA";
        }
        return "DEFAULT";
    }

    /**
     * Check if country is in EU
     */
    private boolean isEUCountry(String country) {
        String[] euCountries = {"DE", "FR", "IT", "ES", "NL", "BE", "AT", "SE", "DK", "FI", "IE", "PT", "GR", "LU"};
        for (String euCountry : euCountries) {
            if (euCountry.equals(country)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if item is a digital good
     */
    private boolean isDigitalGood(CartItem item) {
        // This would typically check product categories or attributes
        String categoryId = item.getCategoryId();
        return categoryId != null && (
            categoryId.contains("digital") ||
            categoryId.contains("software") ||
            categoryId.contains("ebook") ||
            categoryId.contains("music") ||
            categoryId.contains("video")
        );
    }

    /**
     * Check if item requires environmental tax
     */
    private boolean requiresEnvironmentalTax(CartItem item) {
        String categoryId = item.getCategoryId();
        return categoryId != null && (
            categoryId.contains("electronics") ||
            categoryId.contains("automotive") ||
            categoryId.contains("chemicals")
        );
    }

    /**
     * Calculate environmental tax for item
     */
    private BigDecimal calculateEnvironmentalTax(CartItem item) {
        // Simple environmental tax calculation
        BigDecimal baseAmount = new BigDecimal("2.00"); // $2 base environmental fee
        return baseAmount.multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    /**
     * Calculate effective tax rate
     */
    private double calculateEffectiveTaxRate(Map<String, Object> baseTax, BigDecimal totalTax) {
        try {
            BigDecimal subtotal = (BigDecimal) baseTax.get("subtotal");
            if (subtotal.compareTo(BigDecimal.ZERO) == 0) {
                return 0.0;
            }
            
            return totalTax.divide(subtotal, 4, RoundingMode.HALF_UP)
                         .multiply(BigDecimal.valueOf(100))
                         .doubleValue();
                         
        } catch (Exception e) {
            log.warn("Error calculating effective tax rate: {}", e.getMessage());
            return 0.0;
        }
    }
}
