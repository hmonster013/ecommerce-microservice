package org.de013.productcatalog.dto.search;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for comprehensive search analytics report.
 */
@Data
@Builder
@Schema(description = "Comprehensive search analytics report")
public class SearchReportDto {

    @Schema(description = "Report generation timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime generatedAt;

    @Schema(description = "Report period start")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime periodStart;

    @Schema(description = "Report period end")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime periodEnd;

    @Schema(description = "Report type: DAILY, WEEKLY, MONTHLY, CUSTOM")
    private String reportType;

    @Schema(description = "Executive summary of search performance")
    private ExecutiveSummary executiveSummary;

    @Schema(description = "Detailed search performance metrics")
    private SearchPerformanceDto performance;

    @Schema(description = "Search insights and analytics")
    private SearchInsightsDto insights;

    @Schema(description = "Top performing search queries")
    private List<PopularQueryDto> topQueries;

    @Schema(description = "Queries that need attention (no results)")
    private List<PopularQueryDto> problemQueries;

    @Schema(description = "Search trends and patterns")
    private TrendsAnalysis trends;

    @Schema(description = "User behavior analysis")
    private UserBehaviorAnalysis userBehavior;

    @Schema(description = "Search optimization recommendations")
    private List<OptimizationRecommendation> recommendations;

    @Schema(description = "Key performance indicators")
    private Map<String, Object> kpis;

    @Schema(description = "Comparative analysis with previous period")
    private ComparativeAnalysis comparison;

    /**
     * Executive summary section
     */
    @Data
    @Builder
    public static class ExecutiveSummary {
        private String overallStatus;
        private Integer performanceScore;
        private String keyAchievement;
        private String primaryConcern;
        private String topRecommendation;
        private Map<String, String> highlights;
    }

    /**
     * Trends analysis section
     */
    @Data
    @Builder
    public static class TrendsAnalysis {
        private Map<String, Long> searchVolumeTrends;
        private Map<String, Double> performanceTrends;
        private List<String> emergingQueries;
        private List<String> decliningQueries;
        private Map<String, Object> seasonalPatterns;
    }

    /**
     * User behavior analysis section
     */
    @Data
    @Builder
    public static class UserBehaviorAnalysis {
        private Double averageSessionDuration;
        private Double averageSearchesPerSession;
        private Double bounceRate;
        private Map<String, Long> deviceDistribution;
        private Map<String, Long> locationDistribution;
        private List<String> commonSearchPaths;
    }

    /**
     * Optimization recommendation
     */
    @Data
    @Builder
    public static class OptimizationRecommendation {
        private String category;
        private String priority; // HIGH, MEDIUM, LOW
        private String title;
        private String description;
        private String expectedImpact;
        private String effort; // LOW, MEDIUM, HIGH
        private List<String> actionItems;
        private Map<String, Object> metrics;
    }

    /**
     * Comparative analysis with previous period
     */
    @Data
    @Builder
    public static class ComparativeAnalysis {
        private Double searchVolumeChange;
        private Double performanceChange;
        private Double conversionRateChange;
        private Double errorRateChange;
        private List<String> improvements;
        private List<String> regressions;
        private String overallTrend; // IMPROVING, STABLE, DECLINING
    }

    // Helper methods

    /**
     * Get report summary in text format
     */
    @JsonIgnore
    public String getTextSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Search Analytics Report\n");
        summary.append("Period: ").append(periodStart).append(" to ").append(periodEnd).append("\n\n");
        
        if (executiveSummary != null) {
            summary.append("Executive Summary:\n");
            summary.append("- Overall Status: ").append(executiveSummary.getOverallStatus()).append("\n");
            summary.append("- Performance Score: ").append(executiveSummary.getPerformanceScore()).append("/100\n");
            summary.append("- Key Achievement: ").append(executiveSummary.getKeyAchievement()).append("\n");
            summary.append("- Primary Concern: ").append(executiveSummary.getPrimaryConcern()).append("\n\n");
        }
        
        if (performance != null) {
            summary.append("Key Metrics:\n");
            summary.append("- Total Searches: ").append(performance.getTotalSearches()).append("\n");
            summary.append("- Success Rate: ").append(String.format("%.1f%%", performance.getSearchSuccessRate())).append("\n");
            summary.append("- Click-through Rate: ").append(String.format("%.1f%%", performance.getClickThroughRate())).append("\n");
            summary.append("- Conversion Rate: ").append(String.format("%.1f%%", performance.getConversionRate())).append("\n\n");
        }
        
        if (recommendations != null && !recommendations.isEmpty()) {
            summary.append("Top Recommendations:\n");
            recommendations.stream()
                    .filter(rec -> "HIGH".equals(rec.getPriority()))
                    .limit(3)
                    .forEach(rec -> summary.append("- ").append(rec.getTitle()).append("\n"));
        }
        
        return summary.toString();
    }

    /**
     * Check if report shows positive trends
     */
    @JsonIgnore
    public boolean showsPositiveTrends() {
        if (comparison == null) return true;
        
        return comparison.getOverallTrend() != null && 
               "IMPROVING".equals(comparison.getOverallTrend());
    }

    /**
     * Get high priority recommendations count
     */
    @JsonIgnore
    public long getHighPriorityRecommendationsCount() {
        if (recommendations == null) return 0;
        
        return recommendations.stream()
                .filter(rec -> "HIGH".equals(rec.getPriority()))
                .count();
    }

    /**
     * Get critical issues count
     */
    @JsonIgnore
    public long getCriticalIssuesCount() {
        if (problemQueries == null) return 0;
        
        return problemQueries.stream()
                .filter(query -> query.getSearchCount() > 100) // High volume no-result queries
                .count();
    }

    /**
     * Calculate report completeness score
     */
    @JsonIgnore
    public int getCompletenessScore() {
        int score = 0;
        int maxScore = 8;
        
        if (executiveSummary != null) score++;
        if (performance != null) score++;
        if (insights != null) score++;
        if (topQueries != null && !topQueries.isEmpty()) score++;
        if (trends != null) score++;
        if (userBehavior != null) score++;
        if (recommendations != null && !recommendations.isEmpty()) score++;
        if (comparison != null) score++;
        
        return (score * 100) / maxScore;
    }

    /**
     * Get report quality assessment
     */
    @JsonIgnore
    public String getQualityAssessment() {
        int completeness = getCompletenessScore();
        
        if (completeness >= 90) return "EXCELLENT";
        if (completeness >= 75) return "GOOD";
        if (completeness >= 60) return "FAIR";
        return "INCOMPLETE";
    }
}
