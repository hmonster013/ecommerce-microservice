package org.de013.productcatalog.dto.search;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for comprehensive search insights and analytics.
 */
@Data
@Builder
@Schema(description = "Comprehensive search insights and analytics")
public class SearchInsightsDto {

    @Schema(description = "Analysis period start")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime periodStart;

    @Schema(description = "Analysis period end")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime periodEnd;

    @Schema(description = "Overall search performance metrics")
    private SearchPerformanceDto performance;

    @Schema(description = "Top performing search queries")
    private List<PopularQueryDto> topQueries;

    @Schema(description = "Queries with no results that need attention")
    private List<PopularQueryDto> noResultQueries;

    @Schema(description = "Search volume trends by date")
    private Map<String, Long> searchTrends;

    @Schema(description = "Search patterns by hour of day")
    private Map<Integer, Long> hourlyPatterns;

    @Schema(description = "Most used search filters")
    private List<FilterUsageDto> popularFilters;

    @Schema(description = "Most used sort criteria")
    private List<SortUsageDto> popularSortOptions;

    @Schema(description = "Search source distribution (web, mobile, api)")
    private Map<String, Long> searchSources;

    @Schema(description = "Category-wise search distribution")
    private Map<String, Long> categorySearches;

    @Schema(description = "User engagement metrics")
    private UserEngagementDto userEngagement;

    @Schema(description = "Search quality metrics")
    private SearchQualityDto searchQuality;

    @Schema(description = "Key insights and recommendations")
    private List<String> insights;

    @Schema(description = "Action items for search optimization")
    private List<String> actionItems;

    @Schema(description = "Search health status: HEALTHY, WARNING, CRITICAL")
    private String healthStatus;

    @Schema(description = "Overall search score (0-100)")
    private Integer overallScore;

    /**
     * User engagement metrics
     */
    @Data
    @Builder
    public static class UserEngagementDto {
        private Double averageSessionSearches;
        private Double averageClicksPerSearch;
        private Double averageTimeToFirstClick;
        private Double bounceRate;
        private Double repeatSearchRate;
        private Long activeSearchSessions;
    }

    /**
     * Search quality metrics
     */
    @Data
    @Builder
    public static class SearchQualityDto {
        private Double relevanceScore;
        private Double precisionScore;
        private Double recallScore;
        private Double diversityScore;
        private Double freshnessScore;
        private Integer averageResultsPerPage;
        private Double resultSatisfactionRate;
    }

    // Helper methods

    /**
     * Get the most critical issue that needs attention
     */
    public String getCriticalIssue() {
        if (performance == null) {
            return "No performance data available";
        }

        if (performance.getSearchSuccessRate() < 70.0) {
            return "Low search success rate - many queries return no results";
        }

        if (performance.getClickThroughRate() != null && performance.getClickThroughRate() < 40.0) {
            return "Low click-through rate - search results may not be relevant";
        }

        if (performance.getAverageExecutionTime() != null && performance.getAverageExecutionTime() > 1000) {
            return "Slow search performance - optimization needed";
        }

        if (performance.getConversionRate() != null && performance.getConversionRate() < 3.0) {
            return "Low conversion rate - search-to-purchase flow needs improvement";
        }

        return "No critical issues identified";
    }

    /**
     * Get the top opportunity for improvement
     */
    public String getTopOpportunity() {
        if (noResultQueries != null && !noResultQueries.isEmpty()) {
            long totalNoResults = noResultQueries.stream()
                    .mapToLong(PopularQueryDto::getSearchCount)
                    .sum();
            if (totalNoResults > 100) {
                return "Address no-result queries - " + totalNoResults + " searches could be improved";
            }
        }

        if (performance != null && performance.getSuggestionAcceptanceRate() != null && 
            performance.getSuggestionAcceptanceRate() < 40.0) {
            return "Improve search suggestions - low acceptance rate indicates poor suggestion quality";
        }

        if (userEngagement != null && userEngagement.getBounceRate() != null && 
            userEngagement.getBounceRate() > 60.0) {
            return "Reduce search bounce rate - users are not finding what they need";
        }

        return "Focus on maintaining current performance levels";
    }

    /**
     * Generate automated insights based on data
     */
    public List<String> generateAutomatedInsights() {
        List<String> autoInsights = new java.util.ArrayList<>();

        // Performance insights
        if (performance != null) {
            if (performance.getSearchSuccessRate() > 90.0) {
                autoInsights.add("Excellent search success rate - users are finding relevant results");
            }
            
            if (performance.getClickThroughRate() != null && performance.getClickThroughRate() > 70.0) {
                autoInsights.add("High click-through rate indicates relevant search results");
            }
            
            if (performance.getConversionRate() != null && performance.getConversionRate() > 10.0) {
                autoInsights.add("Strong search-to-purchase conversion - search drives sales effectively");
            }
        }

        // Trend insights
        if (searchTrends != null && searchTrends.size() > 1) {
            // Simple trend analysis - compare first and last values
            List<Map.Entry<String, Long>> entries = new java.util.ArrayList<>(searchTrends.entrySet());
            if (entries.size() >= 2) {
                Long firstValue = entries.get(0).getValue();
                Long lastValue = entries.get(entries.size() - 1).getValue();
                
                if (lastValue > firstValue * 1.2) {
                    autoInsights.add("Search volume is trending upward - consider scaling search infrastructure");
                } else if (lastValue < firstValue * 0.8) {
                    autoInsights.add("Search volume is declining - investigate potential issues");
                }
            }
        }

        // Pattern insights
        if (hourlyPatterns != null) {
            Long maxHourlySearches = hourlyPatterns.values().stream().max(Long::compareTo).orElse(0L);
            Integer peakHour = hourlyPatterns.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(maxHourlySearches))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(12);
            
            autoInsights.add("Peak search activity occurs at " + peakHour + ":00 - optimize for this time");
        }

        return autoInsights;
    }

    /**
     * Calculate overall health score
     */
    public Integer calculateOverallScore() {
        if (performance == null) {
            return 0;
        }

        double score = 0.0;
        int components = 0;

        // Performance component (40%)
        if (performance.getSearchEfficiencyScore() != null) {
            score += performance.getSearchEfficiencyScore() * 0.4;
            components++;
        }

        // User engagement component (30%)
        if (userEngagement != null) {
            double engagementScore = 0.0;
            int engagementComponents = 0;

            if (userEngagement.getAverageClicksPerSearch() != null) {
                engagementScore += Math.min(userEngagement.getAverageClicksPerSearch() * 20, 100);
                engagementComponents++;
            }

            if (userEngagement.getBounceRate() != null) {
                engagementScore += (100 - userEngagement.getBounceRate());
                engagementComponents++;
            }

            if (engagementComponents > 0) {
                score += (engagementScore / engagementComponents) * 0.3;
                components++;
            }
        }

        // Quality component (30%)
        if (searchQuality != null) {
            double qualityScore = 0.0;
            int qualityComponents = 0;

            if (searchQuality.getRelevanceScore() != null) {
                qualityScore += searchQuality.getRelevanceScore();
                qualityComponents++;
            }

            if (searchQuality.getResultSatisfactionRate() != null) {
                qualityScore += searchQuality.getResultSatisfactionRate();
                qualityComponents++;
            }

            if (qualityComponents > 0) {
                score += (qualityScore / qualityComponents) * 0.3;
                components++;
            }
        }

        return components > 0 ? Math.max(0, Math.min(100, (int) Math.round(score / components * 100))) : 0;
    }
}
