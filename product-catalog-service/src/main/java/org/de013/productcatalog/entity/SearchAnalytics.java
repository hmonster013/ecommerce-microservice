package org.de013.productcatalog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Entity for tracking search analytics and user search behavior.
 */
@Entity
@Table(name = "search_analytics", indexes = {
    @Index(name = "idx_search_query", columnList = "search_query"),
    @Index(name = "idx_search_date", columnList = "search_date"),
    @Index(name = "idx_result_count", columnList = "result_count"),
    @Index(name = "idx_user_session", columnList = "user_session_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class SearchAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The search query entered by user
     */
    @Column(name = "search_query", nullable = false, length = 500)
    private String searchQuery;

    /**
     * Normalized search query for analytics
     */
    @Column(name = "normalized_query", length = 500)
    private String normalizedQuery;

    /**
     * Number of results returned
     */
    @Column(name = "result_count", nullable = false)
    private Long resultCount;

    /**
     * Search execution time in milliseconds
     */
    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    /**
     * User session ID for tracking
     */
    @Column(name = "user_session_id", length = 100)
    private String userSessionId;

    /**
     * User IP address (hashed for privacy)
     */
    @Column(name = "user_ip_hash", length = 64)
    private String userIpHash;

    /**
     * User agent information
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * Search filters applied (JSON format)
     */
    @Column(name = "applied_filters", columnDefinition = "TEXT")
    private String appliedFilters;

    /**
     * Sort criteria used
     */
    @Column(name = "sort_criteria", length = 100)
    private String sortCriteria;

    /**
     * Page number requested
     */
    @Column(name = "page_number")
    private Integer pageNumber;

    /**
     * Page size requested
     */
    @Column(name = "page_size")
    private Integer pageSize;

    /**
     * Whether user clicked on any result
     */
    @Column(name = "had_clicks")
    @Builder.Default
    private Boolean hadClicks = false;

    /**
     * Position of first clicked result (1-based)
     */
    @Column(name = "first_click_position")
    private Integer firstClickPosition;

    /**
     * Total number of clicks on results
     */
    @Column(name = "total_clicks")
    @Builder.Default
    private Integer totalClicks = 0;

    /**
     * Whether search led to a purchase
     */
    @Column(name = "led_to_purchase")
    @Builder.Default
    private Boolean ledToPurchase = false;

    /**
     * Search source (web, mobile, api)
     */
    @Column(name = "search_source", length = 50)
    private String searchSource;

    /**
     * Language/locale of the search
     */
    @Column(name = "search_locale", length = 10)
    private String searchLocale;

    /**
     * Whether this was an auto-complete search
     */
    @Column(name = "is_autocomplete")
    @Builder.Default
    private Boolean isAutocomplete = false;

    /**
     * Suggested query if original had no results
     */
    @Column(name = "suggested_query", length = 500)
    private String suggestedQuery;

    /**
     * Whether user accepted the suggestion
     */
    @Column(name = "suggestion_accepted")
    @Builder.Default
    private Boolean suggestionAccepted = false;

    /**
     * Search category context
     */
    @Column(name = "category_context", length = 100)
    private String categoryContext;

    /**
     * Search date and time
     */
    @CreatedDate
    @Column(name = "search_date", nullable = false)
    private LocalDateTime searchDate;

    /**
     * Additional metadata (JSON format)
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    // Helper methods

    /**
     * Check if search had no results
     */
    public boolean isNoResultSearch() {
        return resultCount == null || resultCount == 0;
    }

    /**
     * Check if search was successful (had results and clicks)
     */
    public boolean isSuccessfulSearch() {
        return resultCount != null && resultCount > 0 && hadClicks != null && hadClicks;
    }

    /**
     * Calculate click-through rate
     */
    public double getClickThroughRate() {
        if (resultCount == null || resultCount == 0) {
            return 0.0;
        }
        return hadClicks != null && hadClicks ? 1.0 : 0.0;
    }

    /**
     * Check if search was slow (execution time > 1 second)
     */
    public boolean isSlowSearch() {
        return executionTimeMs != null && executionTimeMs > 1000;
    }

    /**
     * Get search effectiveness score (0-100)
     */
    public int getEffectivenessScore() {
        int score = 0;
        
        // Base score for having results
        if (resultCount != null && resultCount > 0) {
            score += 30;
        }
        
        // Bonus for clicks
        if (hadClicks != null && hadClicks) {
            score += 40;
        }
        
        // Bonus for purchase
        if (ledToPurchase != null && ledToPurchase) {
            score += 30;
        }
        
        // Penalty for slow search
        if (isSlowSearch()) {
            score -= 10;
        }
        
        return Math.max(0, Math.min(100, score));
    }

    /**
     * Check if this is a repeat search (same query in session)
     */
    public boolean isRepeatSearch() {
        // This would need to be determined by the service layer
        // by checking for previous searches with same query and session
        return false; // Placeholder
    }

    @Override
    public String toString() {
        return "SearchAnalytics{" +
                "id=" + id +
                ", searchQuery='" + searchQuery + '\'' +
                ", resultCount=" + resultCount +
                ", executionTimeMs=" + executionTimeMs +
                ", hadClicks=" + hadClicks +
                ", searchDate=" + searchDate +
                '}';
    }
}
