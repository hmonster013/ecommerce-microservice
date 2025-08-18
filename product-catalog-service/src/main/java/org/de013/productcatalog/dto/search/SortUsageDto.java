package org.de013.productcatalog.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for sort criteria usage statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Sort criteria usage statistics")
public class SortUsageDto {

    @Schema(description = "Sort criteria (price_asc, name_desc, rating_desc, etc.)", example = "price_asc")
    private String sortCriteria;

    @Schema(description = "Human-readable sort description", example = "Price: Low to High")
    private String sortDescription;

    @Schema(description = "Number of times this sort was used", example = "2340")
    private Long usageCount;

    @Schema(description = "Percentage of total searches using this sort", example = "15.2")
    private Double usagePercentage;

    @Schema(description = "Average click-through rate for this sort", example = "58.7")
    private Double clickThroughRate;

    @Schema(description = "Average conversion rate for this sort", example = "9.4")
    private Double conversionRate;

    @Schema(description = "Rank in popularity among all sort options", example = "2")
    private Integer popularityRank;

    @Schema(description = "Whether this is the default sort", example = "false")
    private Boolean isDefault;

    @Schema(description = "Sort effectiveness score (0-100)", example = "72")
    private Integer effectivenessScore;

    // Helper methods

    /**
     * Check if this is a preferred sort option
     */
    public boolean isPreferred() {
        return usagePercentage != null && usagePercentage > 10.0;
    }

    /**
     * Check if this sort option drives conversions
     */
    public boolean drivesConversions() {
        return conversionRate != null && conversionRate > 8.0;
    }

    /**
     * Get sort category
     */
    public String getSortCategory() {
        if (sortCriteria == null) return "UNKNOWN";
        
        String criteria = sortCriteria.toLowerCase();
        if (criteria.contains("price")) return "PRICE";
        if (criteria.contains("name") || criteria.contains("title")) return "NAME";
        if (criteria.contains("rating") || criteria.contains("review")) return "RATING";
        if (criteria.contains("date") || criteria.contains("created")) return "DATE";
        if (criteria.contains("popular") || criteria.contains("trending")) return "POPULARITY";
        if (criteria.contains("relevance") || criteria.contains("score")) return "RELEVANCE";
        
        return "OTHER";
    }

    /**
     * Calculate effectiveness score
     */
    public Integer calculateEffectivenessScore() {
        double score = 0.0;
        
        // Usage component (40%)
        if (usagePercentage != null) {
            score += Math.min(usagePercentage * 2, 40);
        }
        
        // Click-through rate component (35%)
        if (clickThroughRate != null) {
            score += clickThroughRate * 0.35;
        }
        
        // Conversion rate component (25%)
        if (conversionRate != null) {
            score += Math.min(conversionRate * 2.5, 25);
        }
        
        return Math.max(0, Math.min(100, (int) Math.round(score)));
    }

    /**
     * Get recommendation for this sort option
     */
    public String getRecommendation() {
        if (isPreferred() && drivesConversions()) {
            return "FEATURE - Highly effective sort option, consider making it more prominent";
        }
        
        if (isDefault != null && isDefault && 
            (clickThroughRate == null || clickThroughRate < 50.0)) {
            return "REVIEW_DEFAULT - Default sort may not be optimal for user engagement";
        }
        
        if (usagePercentage != null && usagePercentage < 2.0) {
            return "CONSIDER_REMOVAL - Low usage sort option, evaluate necessity";
        }
        
        if (drivesConversions()) {
            return "MAINTAIN - Good converting sort option, keep available";
        }
        
        return "MONITOR - Continue tracking performance";
    }
}
