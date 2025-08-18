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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shipping Cost Estimation Service
 * Handles comprehensive shipping cost calculations with multiple carriers and methods
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingCostService {

    private final UserServiceFeignClient userServiceFeignClient;

    // Configuration values
    @Value("${shopping-cart.shipping.free-shipping-threshold:50.00}")
    private BigDecimal freeShippingThreshold;

    @Value("${shopping-cart.shipping.standard-rate:5.99}")
    private BigDecimal standardShippingRate;

    @Value("${shopping-cart.shipping.express-rate:12.99}")
    private BigDecimal expressShippingRate;

    @Value("${shopping-cart.shipping.overnight-rate:24.99}")
    private BigDecimal overnightShippingRate;

    @Value("${shopping-cart.shipping.weight-rate-per-lb:1.50}")
    private BigDecimal weightRatePerLb;

    @Value("${shopping-cart.shipping.oversized-fee:15.00}")
    private BigDecimal oversizedFee;

    @Value("${shopping-cart.shipping.handling-fee:2.99}")
    private BigDecimal handlingFee;

    @Value("${shopping-cart.shipping.insurance-rate:0.01}")
    private double insuranceRate;

    // ==================== SHIPPING COST CALCULATION ====================

    /**
     * Calculate comprehensive shipping costs for cart
     */
    public Map<String, Object> calculateShippingCosts(Cart cart) {
        try {
            log.debug("Calculating shipping costs for cart: {}", cart.getId());
            
            Map<String, Object> result = new HashMap<>();
            result.put("cartId", cart.getId());
            result.put("timestamp", LocalDateTime.now());
            
            // Get shipping address
            Map<String, Object> shippingAddress = getShippingAddress(cart.getUserId());
            result.put("shippingAddress", shippingAddress);
            
            // Calculate cart metrics
            Map<String, Object> cartMetrics = calculateCartMetrics(cart);
            result.put("cartMetrics", cartMetrics);
            
            // Calculate shipping options
            List<Map<String, Object>> shippingOptions = calculateShippingOptions(cart, cartMetrics, shippingAddress);
            result.put("shippingOptions", shippingOptions);
            
            // Determine recommended option
            Map<String, Object> recommendedOption = determineRecommendedOption(shippingOptions, cart);
            result.put("recommendedOption", recommendedOption);
            
            // Calculate additional fees
            Map<String, Object> additionalFees = calculateAdditionalFees(cart, cartMetrics);
            result.put("additionalFees", additionalFees);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error calculating shipping costs for cart {}: {}", cart.getId(), e.getMessage(), e);
            
            return Map.of(
                "cartId", cart.getId(),
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
        }
    }

    /**
     * Calculate cart physical metrics
     */
    private Map<String, Object> calculateCartMetrics(Cart cart) {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            BigDecimal totalWeight = BigDecimal.ZERO;
            BigDecimal totalVolume = BigDecimal.ZERO;
            int totalItems = 0;
            boolean hasOversizedItems = false;
            boolean hasFragileItems = false;
            boolean hasHazardousItems = false;
            
            for (CartItem item : cart.getCartItems()) {
                totalItems += item.getQuantity();
                
                // Calculate weight
                BigDecimal itemWeight = item.getWeight() != null ? item.getWeight() : new BigDecimal("1.0");
                totalWeight = totalWeight.add(itemWeight.multiply(BigDecimal.valueOf(item.getQuantity())));
                
                // Calculate volume (simplified)
                BigDecimal itemVolume = calculateItemVolume(item);
                totalVolume = totalVolume.add(itemVolume.multiply(BigDecimal.valueOf(item.getQuantity())));
                
                // Check special handling requirements
                if (isOversizedItem(item)) {
                    hasOversizedItems = true;
                }
                if (isFragileItem(item)) {
                    hasFragileItems = true;
                }
                if (isHazardousItem(item)) {
                    hasHazardousItems = true;
                }
            }
            
            metrics.put("totalWeight", totalWeight);
            metrics.put("totalVolume", totalVolume);
            metrics.put("totalItems", totalItems);
            metrics.put("hasOversizedItems", hasOversizedItems);
            metrics.put("hasFragileItems", hasFragileItems);
            metrics.put("hasHazardousItems", hasHazardousItems);
            
            // Determine shipping class
            String shippingClass = determineShippingClass(totalWeight, totalVolume, hasOversizedItems, hasHazardousItems);
            metrics.put("shippingClass", shippingClass);
            
        } catch (Exception e) {
            log.error("Error calculating cart metrics: {}", e.getMessage());
            metrics.put("error", e.getMessage());
        }
        
        return metrics;
    }

    /**
     * Calculate available shipping options
     */
    private List<Map<String, Object>> calculateShippingOptions(Cart cart, Map<String, Object> cartMetrics, Map<String, Object> shippingAddress) {
        List<Map<String, Object>> options = new ArrayList<>();
        
        try {
            BigDecimal cartTotal = cart.getTotalAmount() != null ? cart.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal totalWeight = (BigDecimal) cartMetrics.get("totalWeight");
            String shippingClass = (String) cartMetrics.get("shippingClass");
            
            // Standard Shipping
            Map<String, Object> standardOption = calculateStandardShipping(cartTotal, totalWeight, shippingClass, shippingAddress);
            options.add(standardOption);
            
            // Express Shipping
            Map<String, Object> expressOption = calculateExpressShipping(cartTotal, totalWeight, shippingClass, shippingAddress);
            options.add(expressOption);
            
            // Overnight Shipping
            if (!"HAZARDOUS".equals(shippingClass)) {
                Map<String, Object> overnightOption = calculateOvernightShipping(cartTotal, totalWeight, shippingClass, shippingAddress);
                options.add(overnightOption);
            }
            
            // Free Shipping (if eligible)
            if (cartTotal.compareTo(freeShippingThreshold) >= 0) {
                Map<String, Object> freeOption = calculateFreeShipping(cartTotal, totalWeight, shippingClass, shippingAddress);
                options.add(freeOption);
            }
            
        } catch (Exception e) {
            log.error("Error calculating shipping options: {}", e.getMessage());
        }
        
        return options;
    }

    /**
     * Calculate standard shipping cost
     */
    private Map<String, Object> calculateStandardShipping(BigDecimal cartTotal, BigDecimal weight, String shippingClass, Map<String, Object> address) {
        Map<String, Object> option = new HashMap<>();
        
        try {
            BigDecimal baseCost = standardShippingRate;
            
            // Weight-based pricing
            if (weight.compareTo(new BigDecimal("5.0")) > 0) {
                BigDecimal extraWeight = weight.subtract(new BigDecimal("5.0"));
                BigDecimal weightCost = extraWeight.multiply(weightRatePerLb);
                baseCost = baseCost.add(weightCost);
            }
            
            // Distance-based pricing
            BigDecimal distanceMultiplier = calculateDistanceMultiplier(address);
            baseCost = baseCost.multiply(distanceMultiplier);
            
            // Shipping class adjustments
            baseCost = applyShippingClassAdjustment(baseCost, shippingClass);
            
            option.put("method", "STANDARD");
            option.put("name", "Standard Shipping");
            option.put("cost", baseCost.setScale(2, RoundingMode.HALF_UP));
            option.put("estimatedDays", determineStandardDeliveryDays(address));
            option.put("description", "5-7 business days");
            option.put("carrier", "USPS");
            option.put("trackingAvailable", true);
            
        } catch (Exception e) {
            log.error("Error calculating standard shipping: {}", e.getMessage());
            option.put("error", e.getMessage());
        }
        
        return option;
    }

    /**
     * Calculate express shipping cost
     */
    private Map<String, Object> calculateExpressShipping(BigDecimal cartTotal, BigDecimal weight, String shippingClass, Map<String, Object> address) {
        Map<String, Object> option = new HashMap<>();
        
        try {
            BigDecimal baseCost = expressShippingRate;
            
            // Weight-based pricing (higher rate for express)
            if (weight.compareTo(new BigDecimal("3.0")) > 0) {
                BigDecimal extraWeight = weight.subtract(new BigDecimal("3.0"));
                BigDecimal weightCost = extraWeight.multiply(weightRatePerLb.multiply(new BigDecimal("1.5")));
                baseCost = baseCost.add(weightCost);
            }
            
            // Distance-based pricing
            BigDecimal distanceMultiplier = calculateDistanceMultiplier(address);
            baseCost = baseCost.multiply(distanceMultiplier);
            
            // Shipping class adjustments
            baseCost = applyShippingClassAdjustment(baseCost, shippingClass);
            
            option.put("method", "EXPRESS");
            option.put("name", "Express Shipping");
            option.put("cost", baseCost.setScale(2, RoundingMode.HALF_UP));
            option.put("estimatedDays", determineExpressDeliveryDays(address));
            option.put("description", "2-3 business days");
            option.put("carrier", "FedEx");
            option.put("trackingAvailable", true);
            
        } catch (Exception e) {
            log.error("Error calculating express shipping: {}", e.getMessage());
            option.put("error", e.getMessage());
        }
        
        return option;
    }

    /**
     * Calculate overnight shipping cost
     */
    private Map<String, Object> calculateOvernightShipping(BigDecimal cartTotal, BigDecimal weight, String shippingClass, Map<String, Object> address) {
        Map<String, Object> option = new HashMap<>();
        
        try {
            BigDecimal baseCost = overnightShippingRate;
            
            // Weight-based pricing (premium rate for overnight)
            BigDecimal weightCost = weight.multiply(weightRatePerLb.multiply(new BigDecimal("2.0")));
            baseCost = baseCost.add(weightCost);
            
            // Distance-based pricing
            BigDecimal distanceMultiplier = calculateDistanceMultiplier(address);
            baseCost = baseCost.multiply(distanceMultiplier);
            
            // Shipping class adjustments
            baseCost = applyShippingClassAdjustment(baseCost, shippingClass);
            
            option.put("method", "OVERNIGHT");
            option.put("name", "Overnight Shipping");
            option.put("cost", baseCost.setScale(2, RoundingMode.HALF_UP));
            option.put("estimatedDays", 1);
            option.put("description", "Next business day");
            option.put("carrier", "UPS");
            option.put("trackingAvailable", true);
            option.put("signatureRequired", true);
            
        } catch (Exception e) {
            log.error("Error calculating overnight shipping: {}", e.getMessage());
            option.put("error", e.getMessage());
        }
        
        return option;
    }

    /**
     * Calculate free shipping option
     */
    private Map<String, Object> calculateFreeShipping(BigDecimal cartTotal, BigDecimal weight, String shippingClass, Map<String, Object> address) {
        Map<String, Object> option = new HashMap<>();
        
        try {
            option.put("method", "FREE");
            option.put("name", "Free Shipping");
            option.put("cost", BigDecimal.ZERO);
            option.put("estimatedDays", determineStandardDeliveryDays(address) + 1); // Slightly longer
            option.put("description", "6-8 business days");
            option.put("carrier", "USPS");
            option.put("trackingAvailable", true);
            option.put("qualificationThreshold", freeShippingThreshold);
            
        } catch (Exception e) {
            log.error("Error calculating free shipping: {}", e.getMessage());
            option.put("error", e.getMessage());
        }
        
        return option;
    }

    /**
     * Calculate additional fees
     */
    private Map<String, Object> calculateAdditionalFees(Cart cart, Map<String, Object> cartMetrics) {
        Map<String, Object> fees = new HashMap<>();
        
        try {
            BigDecimal totalFees = BigDecimal.ZERO;
            
            // Handling fee
            fees.put("handlingFee", handlingFee);
            totalFees = totalFees.add(handlingFee);
            
            // Oversized item fee
            if (Boolean.TRUE.equals(cartMetrics.get("hasOversizedItems"))) {
                fees.put("oversizedFee", oversizedFee);
                totalFees = totalFees.add(oversizedFee);
            }
            
            // Insurance (optional)
            BigDecimal cartValue = cart.getTotalAmount() != null ? cart.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal insuranceCost = cartValue.multiply(BigDecimal.valueOf(insuranceRate))
                .setScale(2, RoundingMode.HALF_UP);
            fees.put("insuranceCost", insuranceCost);
            
            // Fragile item handling
            if (Boolean.TRUE.equals(cartMetrics.get("hasFragileItems"))) {
                BigDecimal fragileFee = new BigDecimal("5.00");
                fees.put("fragileHandlingFee", fragileFee);
                totalFees = totalFees.add(fragileFee);
            }
            
            // Hazardous material fee
            if (Boolean.TRUE.equals(cartMetrics.get("hasHazardousItems"))) {
                BigDecimal hazmatFee = new BigDecimal("25.00");
                fees.put("hazmatFee", hazmatFee);
                totalFees = totalFees.add(hazmatFee);
            }
            
            fees.put("totalAdditionalFees", totalFees);
            
        } catch (Exception e) {
            log.error("Error calculating additional fees: {}", e.getMessage());
            fees.put("error", e.getMessage());
        }
        
        return fees;
    }

    // ==================== HELPER METHODS ====================

    /**
     * Get shipping address for user
     */
    private Map<String, Object> getShippingAddress(String userId) {
        try {
            if (userId == null) {
                return Map.of("domestic", true, "zone", "LOCAL");
            }
            
            Map<String, Object> address = userServiceFeignClient.getDefaultShippingAddress(userId);
            
            if (Boolean.TRUE.equals(address.get("fallback")) || !Boolean.TRUE.equals(address.get("hasAddress"))) {
                return Map.of("domestic", true, "zone", "LOCAL");
            }
            
            // Determine shipping zone
            String country = (String) address.getOrDefault("country", "US");
            String state = (String) address.getOrDefault("state", "");
            String zone = determineShippingZone(country, state);
            
            Map<String, Object> shippingInfo = new HashMap<>(address);
            shippingInfo.put("domestic", "US".equals(country));
            shippingInfo.put("zone", zone);
            
            return shippingInfo;
            
        } catch (Exception e) {
            log.warn("Error getting shipping address: {}", e.getMessage());
            return Map.of("domestic", true, "zone", "LOCAL");
        }
    }

    /**
     * Calculate item volume
     */
    private BigDecimal calculateItemVolume(CartItem item) {
        try {
            String dimensions = item.getDimensions();
            if (dimensions != null && dimensions.contains("x")) {
                String[] dims = dimensions.split("x");
                if (dims.length == 3) {
                    BigDecimal length = new BigDecimal(dims[0].trim());
                    BigDecimal width = new BigDecimal(dims[1].trim());
                    BigDecimal height = new BigDecimal(dims[2].trim());
                    return length.multiply(width).multiply(height);
                }
            }
        } catch (Exception e) {
            log.debug("Error parsing dimensions for item {}: {}", item.getId(), e.getMessage());
        }
        
        // Default volume based on price (rough estimate)
        return item.getUnitPrice().divide(new BigDecimal("10"), 2, RoundingMode.HALF_UP);
    }

    /**
     * Determine shipping class
     */
    private String determineShippingClass(BigDecimal weight, BigDecimal volume, boolean oversized, boolean hazardous) {
        if (hazardous) {
            return "HAZARDOUS";
        } else if (oversized || weight.compareTo(new BigDecimal("50")) > 0) {
            return "OVERSIZED";
        } else if (weight.compareTo(new BigDecimal("20")) > 0) {
            return "HEAVY";
        } else {
            return "STANDARD";
        }
    }

    /**
     * Calculate distance multiplier based on shipping zone
     */
    @Cacheable(value = "shipping-zones", key = "#address.get('zone')")
    private BigDecimal calculateDistanceMultiplier(Map<String, Object> address) {
        String zone = (String) address.getOrDefault("zone", "LOCAL");
        
        return switch (zone) {
            case "LOCAL" -> new BigDecimal("1.0");
            case "REGIONAL" -> new BigDecimal("1.2");
            case "NATIONAL" -> new BigDecimal("1.5");
            case "INTERNATIONAL" -> new BigDecimal("2.5");
            default -> new BigDecimal("1.0");
        };
    }

    /**
     * Apply shipping class cost adjustments
     */
    private BigDecimal applyShippingClassAdjustment(BigDecimal baseCost, String shippingClass) {
        return switch (shippingClass) {
            case "HAZARDOUS" -> baseCost.multiply(new BigDecimal("2.0"));
            case "OVERSIZED" -> baseCost.multiply(new BigDecimal("1.5"));
            case "HEAVY" -> baseCost.multiply(new BigDecimal("1.3"));
            default -> baseCost;
        };
    }

    /**
     * Determine shipping zone
     */
    private String determineShippingZone(String country, String state) {
        if (!"US".equals(country)) {
            return "INTERNATIONAL";
        }
        
        // Simplified zone determination
        String[] localStates = {"CA", "NV", "OR", "WA"};
        for (String localState : localStates) {
            if (localState.equals(state)) {
                return "LOCAL";
            }
        }
        
        String[] regionalStates = {"AZ", "UT", "ID", "MT", "WY", "CO", "NM"};
        for (String regionalState : regionalStates) {
            if (regionalState.equals(state)) {
                return "REGIONAL";
            }
        }
        
        return "NATIONAL";
    }

    /**
     * Determine recommended shipping option
     */
    private Map<String, Object> determineRecommendedOption(List<Map<String, Object>> options, Cart cart) {
        try {
            // Find the best value option (balance of cost and speed)
            Map<String, Object> recommended = null;
            double bestScore = 0.0;
            
            for (Map<String, Object> option : options) {
                BigDecimal cost = (BigDecimal) option.get("cost");
                Integer days = (Integer) option.get("estimatedDays");
                
                if (cost != null && days != null) {
                    // Simple scoring: lower cost and faster delivery = higher score
                    double costScore = 100.0 / (cost.doubleValue() + 1.0);
                    double speedScore = 10.0 / (days + 1.0);
                    double totalScore = costScore + speedScore;
                    
                    if (totalScore > bestScore) {
                        bestScore = totalScore;
                        recommended = option;
                    }
                }
            }
            
            if (recommended != null) {
                Map<String, Object> result = new HashMap<>(recommended);
                result.put("recommended", true);
                result.put("score", bestScore);
                return result;
            }
            
        } catch (Exception e) {
            log.error("Error determining recommended option: {}", e.getMessage());
        }
        
        return options.isEmpty() ? Map.of() : options.get(0);
    }

    /**
     * Check if item is oversized
     */
    private boolean isOversizedItem(CartItem item) {
        try {
            String dimensions = item.getDimensions();
            if (dimensions != null && dimensions.contains("x")) {
                String[] dims = dimensions.split("x");
                if (dims.length == 3) {
                    for (String dim : dims) {
                        if (Double.parseDouble(dim.trim()) > 36.0) { // 36 inches
                            return true;
                        }
                    }
                }
            }
            
            // Check weight
            BigDecimal weight = item.getWeight();
            return weight != null && weight.compareTo(new BigDecimal("50")) > 0;
            
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if item is fragile
     */
    private boolean isFragileItem(CartItem item) {
        String categoryId = item.getCategoryId();
        return categoryId != null && (
            categoryId.contains("glass") ||
            categoryId.contains("ceramic") ||
            categoryId.contains("electronics") ||
            categoryId.contains("fragile")
        );
    }

    /**
     * Check if item is hazardous
     */
    private boolean isHazardousItem(CartItem item) {
        String categoryId = item.getCategoryId();
        return categoryId != null && (
            categoryId.contains("chemical") ||
            categoryId.contains("battery") ||
            categoryId.contains("flammable") ||
            categoryId.contains("hazmat")
        );
    }

    /**
     * Determine standard delivery days
     */
    private int determineStandardDeliveryDays(Map<String, Object> address) {
        String zone = (String) address.getOrDefault("zone", "LOCAL");
        return switch (zone) {
            case "LOCAL" -> 3;
            case "REGIONAL" -> 5;
            case "NATIONAL" -> 7;
            case "INTERNATIONAL" -> 14;
            default -> 5;
        };
    }

    /**
     * Determine express delivery days
     */
    private int determineExpressDeliveryDays(Map<String, Object> address) {
        String zone = (String) address.getOrDefault("zone", "LOCAL");
        return switch (zone) {
            case "LOCAL" -> 1;
            case "REGIONAL" -> 2;
            case "NATIONAL" -> 3;
            case "INTERNATIONAL" -> 7;
            default -> 2;
        };
    }
}
