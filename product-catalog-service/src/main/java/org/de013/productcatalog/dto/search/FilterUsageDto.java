package org.de013.productcatalog.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for search filter usage statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Search filter usage statistics")
public class FilterUsageDto {

    @Schema(description = "Filter type (category, price, brand, rating, etc.)", example = "category")
    private String filterType;

    @Schema(description = "Filter value or criteria", example = "Electronics")
    private String filterValue;

    @Schema(description = "Number of times this filter was used", example = "1250")
    private Long usageCount;

    @Schema(description = "Percentage of total searches using this filter", example = "8.5")
    private Double usagePercentage;

    @Schema(description = "Average number of results when this filter is applied", example = "45.2")
    private Double averageResults;

    @Schema(description = "Click-through rate when this filter is applied", example = "65.4")
    private Double clickThroughRate;

    @Schema(description = "Conversion rate when this filter is applied", example = "12.3")
    private Double conversionRate;

    @Schema(description = "Rank in popularity among all filters", example = "3")
    private Integer popularityRank;

    @Schema(description = "Trend indicator: RISING, STABLE, FALLING", example = "RISING")
    private String trend;

    @Schema(description = "Filter effectiveness score (0-100)", example = "78")
    private Integer effectivenessScore;

    // Helper methods

    /**
     * Check if this is a high-impact filter
     */
    public boolean isHighImpact() {
        return usagePercentage != null && usagePercentage > 5.0 &&
               clickThroughRate != null && clickThroughRate > 50.0;
    }

    /**
     * Check if this filter improves search results
     */
    public boolean improvesResults() {
        return clickThroughRate != null && clickThroughRate > 60.0 &&
               conversionRate != null && conversionRate > 8.0;
    }

    /**
     * Get filter category for grouping
     */
    public String getFilterCategory() {
        if (filterType == null) return "UNKNOWN";
        
        switch (filterType.toLowerCase()) {
            case "category":
            case "subcategory":
                return "CATEGORY";
            case "price":
            case "pricerange":
                return "PRICE";
            case "brand":
            case "manufacturer":
                return "BRAND";
            case "rating":
            case "review":
                return "RATING";
            case "availability":
            case "instock":
                return "AVAILABILITY";
            case "color":
            case "size":
            case "material":
                return "ATTRIBUTES";
            default:
                return "OTHER";
        }
    }

    /**
     * Calculate effectiveness score based on usage and performance
     */
    public Integer calculateEffectivenessScore() {
        double score = 0.0;
        
        // Usage component (30%)
        if (usagePercentage != null) {
            score += Math.min(usagePercentage * 3, 30); // Cap at 30 for scoring
        }
        
        // Click-through rate component (40%)
        if (clickThroughRate != null) {
            score += clickThroughRate * 0.4;
        }
        
        // Conversion rate component (30%)
        if (conversionRate != null) {
            score += Math.min(conversionRate * 2, 30); // Scale and cap conversion rate
        }
        
        return Math.max(0, Math.min(100, (int) Math.round(score)));
    }

    /**
     * Get recommendation for this filter
     */
    public String getRecommendation() {
        if (isHighImpact() && improvesResults()) {
            return "PROMOTE - This filter is highly effective and should be prominently featured";
        }
        
        if (usagePercentage != null && usagePercentage > 10.0 && 
            (clickThroughRate == null || clickThroughRate < 40.0)) {
            return "OPTIMIZE - Popular filter but low performance, needs improvement";
        }
        
        if (usagePercentage != null && usagePercentage < 1.0) {
            return "EVALUATE - Low usage filter, consider removing or repositioning";
        }
        
        if (improvesResults()) {
            return "MAINTAIN - Good performing filter, keep current implementation";
        }
        
        return "MONITOR - Continue tracking performance";
    }
}


