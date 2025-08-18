package org.de013.productcatalog.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for popular search queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Popular search query with usage statistics")
public class PopularQueryDto {

    @Schema(description = "Search query text", example = "wireless headphones")
    private String query;

    @Schema(description = "Number of times this query was searched", example = "1250")
    private Long searchCount;

    @Schema(description = "Average number of results for this query", example = "45.2")
    private Double averageResults;

    @Schema(description = "Click-through rate for this query as percentage", example = "65.4")
    private Double clickThroughRate;

    @Schema(description = "Conversion rate for this query as percentage", example = "12.3")
    private Double conversionRate;

    @Schema(description = "Average execution time for this query in milliseconds", example = "234")
    private Double averageExecutionTime;

    @Schema(description = "Rank/position in popularity", example = "1")
    private Integer rank;

    @Schema(description = "Percentage of total searches", example = "8.1")
    private Double searchPercentage;

    @Schema(description = "Trend indicator: RISING, STABLE, FALLING", example = "RISING")
    private String trend;

    @Schema(description = "Related search queries")
    private String[] relatedQueries;

    @Schema(description = "Suggested improvements for this query")
    private String[] suggestions;

    // Helper methods

    /**
     * Check if this is a high-performing query
     */
    public boolean isHighPerforming() {
        return clickThroughRate != null && clickThroughRate > 60.0 &&
               conversionRate != null && conversionRate > 10.0;
    }

    /**
     * Check if this query needs optimization
     */
    public boolean needsOptimization() {
        return clickThroughRate != null && clickThroughRate < 30.0 ||
               averageExecutionTime != null && averageExecutionTime > 1000;
    }

    /**
     * Get performance score (0-100)
     */
    public int getPerformanceScore() {
        double score = 0.0;
        
        if (clickThroughRate != null) {
            score += clickThroughRate * 0.4;
        }
        
        if (conversionRate != null) {
            score += Math.min(conversionRate * 3, 30) * 0.4; // Cap at 30 for scoring
        }
        
        if (averageExecutionTime != null) {
            // Performance component - faster is better
            double performanceScore = Math.max(0, 100 - (averageExecutionTime / 10));
            score += performanceScore * 0.2;
        }
        
        return Math.max(0, Math.min(100, (int) Math.round(score)));
    }

    /**
     * Get query category based on performance
     */
    public String getCategory() {
        int score = getPerformanceScore();
        if (score >= 80) return "TOP_PERFORMER";
        if (score >= 60) return "GOOD_PERFORMER";
        if (score >= 40) return "AVERAGE_PERFORMER";
        return "NEEDS_IMPROVEMENT";
    }
}
