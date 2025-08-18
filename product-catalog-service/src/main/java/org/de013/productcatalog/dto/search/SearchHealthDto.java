package org.de013.productcatalog.dto.search;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for search system health status.
 */
@Data
@Builder
@Schema(description = "Search system health status and metrics")
public class SearchHealthDto {

    @Schema(description = "Overall health status: HEALTHY, WARNING, CRITICAL", example = "HEALTHY")
    private String status;

    @Schema(description = "Health check timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    @Schema(description = "Overall health score (0-100)", example = "95")
    private Integer healthScore;

    @Schema(description = "Search service availability percentage", example = "99.9")
    private Double availability;

    @Schema(description = "Average response time in milliseconds", example = "150")
    private Double averageResponseTime;

    @Schema(description = "Error rate percentage", example = "0.1")
    private Double errorRate;

    @Schema(description = "Search success rate percentage", example = "92.5")
    private Double successRate;

    @Schema(description = "Current search load (requests per minute)", example = "450")
    private Long currentLoad;

    @Schema(description = "Maximum search capacity (requests per minute)", example = "1000")
    private Long maxCapacity;

    @Schema(description = "Cache hit rate percentage", example = "78.5")
    private Double cacheHitRate;

    @Schema(description = "Index size in MB", example = "2048")
    private Long indexSizeBytes;

    @Schema(description = "Last index update timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastIndexUpdate;

    @Schema(description = "Number of indexed documents", example = "15420")
    private Long indexedDocuments;

    @Schema(description = "Search queue size", example = "5")
    private Integer queueSize;

    @Schema(description = "Active search connections", example = "23")
    private Integer activeConnections;

    @Schema(description = "Memory usage percentage", example = "65.2")
    private Double memoryUsage;

    @Schema(description = "CPU usage percentage", example = "45.8")
    private Double cpuUsage;

    @Schema(description = "Disk usage percentage", example = "72.1")
    private Double diskUsage;

    @Schema(description = "Health check details by component")
    private Map<String, ComponentHealth> componentHealth;

    @Schema(description = "Recent errors and warnings")
    private List<HealthIssue> issues;

    @Schema(description = "Performance metrics over time")
    private Map<String, Double> performanceMetrics;

    @Schema(description = "Recommendations for improvement")
    private List<String> recommendations;

    /**
     * Component health details
     */
    @Data
    @Builder
    public static class ComponentHealth {
        private String status;
        private String message;
        private Double responseTime;
        private LocalDateTime lastCheck;
        private Map<String, Object> metrics;
    }

    /**
     * Health issue details
     */
    @Data
    @Builder
    public static class HealthIssue {
        private String severity; // INFO, WARNING, ERROR, CRITICAL
        private String component;
        private String message;
        private String code;
        private LocalDateTime timestamp;
        private Map<String, Object> details;
    }

    // Helper methods

    /**
     * Check if search system is healthy
     */
    public boolean isHealthy() {
        return "HEALTHY".equals(status) && healthScore != null && healthScore >= 80;
    }

    /**
     * Check if system is under high load
     */
    public boolean isUnderHighLoad() {
        if (currentLoad == null || maxCapacity == null || maxCapacity == 0) {
            return false;
        }
        return (double) currentLoad / maxCapacity > 0.8;
    }

    /**
     * Check if system has performance issues
     */
    public boolean hasPerformanceIssues() {
        return (averageResponseTime != null && averageResponseTime > 1000) ||
               (errorRate != null && errorRate > 5.0) ||
               (successRate != null && successRate < 90.0);
    }

    /**
     * Check if system has resource issues
     */
    public boolean hasResourceIssues() {
        return (memoryUsage != null && memoryUsage > 90.0) ||
               (cpuUsage != null && cpuUsage > 90.0) ||
               (diskUsage != null && diskUsage > 90.0);
    }

    /**
     * Get critical issues count
     */
    public long getCriticalIssuesCount() {
        if (issues == null) return 0;
        return issues.stream()
                .filter(issue -> "CRITICAL".equals(issue.getSeverity()))
                .count();
    }

    /**
     * Get warning issues count
     */
    public long getWarningIssuesCount() {
        if (issues == null) return 0;
        return issues.stream()
                .filter(issue -> "WARNING".equals(issue.getSeverity()))
                .count();
    }

    /**
     * Calculate capacity utilization percentage
     */
    public Double getCapacityUtilization() {
        if (currentLoad == null || maxCapacity == null || maxCapacity == 0) {
            return 0.0;
        }
        return (double) currentLoad / maxCapacity * 100.0;
    }

    /**
     * Get overall system status description
     */
    public String getStatusDescription() {
        if (isHealthy()) {
            return "Search system is operating normally";
        }
        
        if (hasResourceIssues()) {
            return "Search system has resource constraints";
        }
        
        if (hasPerformanceIssues()) {
            return "Search system has performance issues";
        }
        
        if (isUnderHighLoad()) {
            return "Search system is under high load";
        }
        
        return "Search system status unknown";
    }

    /**
     * Generate automated recommendations
     */
    public List<String> generateRecommendations() {
        List<String> autoRecommendations = new java.util.ArrayList<>();
        
        if (isUnderHighLoad()) {
            autoRecommendations.add("Consider scaling search infrastructure - current load is " + 
                                  String.format("%.1f%%", getCapacityUtilization()));
        }
        
        if (averageResponseTime != null && averageResponseTime > 1000) {
            autoRecommendations.add("Optimize search performance - average response time is " + 
                                  String.format("%.0fms", averageResponseTime));
        }
        
        if (cacheHitRate != null && cacheHitRate < 70.0) {
            autoRecommendations.add("Improve cache strategy - current hit rate is " + 
                                  String.format("%.1f%%", cacheHitRate));
        }
        
        if (errorRate != null && errorRate > 2.0) {
            autoRecommendations.add("Investigate search errors - current error rate is " + 
                                  String.format("%.1f%%", errorRate));
        }
        
        if (memoryUsage != null && memoryUsage > 85.0) {
            autoRecommendations.add("Monitor memory usage - currently at " + 
                                  String.format("%.1f%%", memoryUsage));
        }
        
        if (autoRecommendations.isEmpty()) {
            autoRecommendations.add("Search system is performing well - maintain current configuration");
        }
        
        return autoRecommendations;
    }
}
