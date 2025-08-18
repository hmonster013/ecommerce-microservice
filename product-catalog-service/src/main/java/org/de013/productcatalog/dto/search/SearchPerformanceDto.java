package org.de013.productcatalog.dto.search;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO for search performance metrics.
 */
@Data
@Builder
@Schema(description = "Search performance metrics and statistics")
public class SearchPerformanceDto {

    @Schema(description = "Total number of searches", example = "15420")
    private Long totalSearches;

    @Schema(description = "Number of searches with results", example = "14230")
    private Long searchesWithResults;

    @Schema(description = "Number of searches with no results", example = "1190")
    private Long noResultSearches;

    @Schema(description = "Number of searches that led to clicks", example = "8950")
    private Long searchesWithClicks;

    @Schema(description = "Number of searches that led to purchases", example = "1250")
    private Long searchesToPurchase;

    @Schema(description = "Average search execution time in milliseconds", example = "245.5")
    private Double averageExecutionTime;

    @Schema(description = "Maximum search execution time in milliseconds", example = "2340")
    private Long maxExecutionTime;

    @Schema(description = "Minimum search execution time in milliseconds", example = "45")
    private Long minExecutionTime;

    @Schema(description = "Number of slow searches (>1 second)", example = "156")
    private Long slowSearches;

    @Schema(description = "Average number of results per search", example = "23.4")
    private Double averageResultCount;

    @Schema(description = "Average position of first click", example = "3.2")
    private Double averageClickPosition;

    @Schema(description = "Average number of clicks per search", example = "1.8")
    private Double averageClicksPerSearch;

    @Schema(description = "Click-through rate as percentage", example = "58.1")
    private Double clickThroughRate;

    @Schema(description = "Search to purchase conversion rate as percentage", example = "8.1")
    private Double conversionRate;

    @Schema(description = "Number of searches with suggestions", example = "890")
    private Long searchesWithSuggestions;

    @Schema(description = "Number of accepted suggestions", example = "445")
    private Long acceptedSuggestions;

    @Schema(description = "Suggestion acceptance rate as percentage", example = "50.0")
    private Double suggestionAcceptanceRate;

    @Schema(description = "Number of autocomplete searches", example = "3420")
    private Long autocompleteSearches;

    @Schema(description = "Number of unique search queries", example = "8750")
    private Long uniqueQueries;

    @Schema(description = "Number of repeat searches", example = "2340")
    private Long repeatSearches;

    @Schema(description = "Average searches per session", example = "2.3")
    private Double averageSearchesPerSession;

    @Schema(description = "Period start date")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime periodStart;

    @Schema(description = "Period end date")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime periodEnd;

    @Schema(description = "Data collection timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    // Calculated metrics

    /**
     * Calculate search success rate (searches with results / total searches)
     */
    public Double getSearchSuccessRate() {
        if (totalSearches == null || totalSearches == 0) {
            return 0.0;
        }
        return (searchesWithResults != null ? searchesWithResults : 0) * 100.0 / totalSearches;
    }

    /**
     * Calculate search efficiency score (0-100)
     */
    public Integer getSearchEfficiencyScore() {
        double score = 0.0;
        
        // Success rate component (40%)
        score += getSearchSuccessRate() * 0.4;
        
        // Click-through rate component (30%)
        if (clickThroughRate != null) {
            score += clickThroughRate * 0.3;
        }
        
        // Conversion rate component (20%)
        if (conversionRate != null) {
            score += conversionRate * 2.0 * 0.2; // Scale up conversion rate
        }
        
        // Performance component (10%) - inverse of slow search percentage
        if (totalSearches != null && totalSearches > 0 && slowSearches != null) {
            double slowSearchPercentage = slowSearches * 100.0 / totalSearches;
            score += (100.0 - slowSearchPercentage) * 0.1;
        }
        
        return Math.max(0, Math.min(100, (int) Math.round(score)));
    }

    /**
     * Check if search performance is healthy
     */
    public Boolean isHealthy() {
        return getSearchEfficiencyScore() >= 70 && 
               (averageExecutionTime == null || averageExecutionTime < 500) &&
               getSearchSuccessRate() >= 80.0;
    }

    /**
     * Get performance status
     */
    public String getPerformanceStatus() {
        int score = getSearchEfficiencyScore();
        if (score >= 90) return "EXCELLENT";
        if (score >= 80) return "GOOD";
        if (score >= 70) return "FAIR";
        if (score >= 60) return "POOR";
        return "CRITICAL";
    }

    /**
     * Get performance recommendations
     */
    public String[] getRecommendations() {
        java.util.List<String> recommendations = new java.util.ArrayList<>();
        
        if (getSearchSuccessRate() < 80.0) {
            recommendations.add("Improve search algorithm to increase result relevance");
        }
        
        if (clickThroughRate != null && clickThroughRate < 50.0) {
            recommendations.add("Optimize search result presentation and ranking");
        }
        
        if (conversionRate != null && conversionRate < 5.0) {
            recommendations.add("Improve product recommendations and search-to-purchase flow");
        }
        
        if (averageExecutionTime != null && averageExecutionTime > 500) {
            recommendations.add("Optimize search performance and consider caching strategies");
        }
        
        if (suggestionAcceptanceRate != null && suggestionAcceptanceRate < 30.0) {
            recommendations.add("Improve search suggestion quality and relevance");
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Search performance is optimal - maintain current strategies");
        }
        
        return recommendations.toArray(new String[0]);
    }
}
