package org.de013.productcatalog.repository;

import org.de013.productcatalog.entity.SearchAnalytics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for search analytics operations.
 */
@Repository
public interface SearchAnalyticsRepository extends JpaRepository<SearchAnalytics, Long> {

    // Popular search queries
    @Query("SELECT sa.normalizedQuery, COUNT(sa) as searchCount " +
           "FROM SearchAnalytics sa " +
           "WHERE sa.searchDate >= :since " +
           "GROUP BY sa.normalizedQuery " +
           "ORDER BY searchCount DESC")
    List<Object[]> findPopularQueries(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT sa.normalizedQuery, COUNT(sa) as searchCount " +
           "FROM SearchAnalytics sa " +
           "WHERE sa.searchDate >= :since AND sa.resultCount > 0 " +
           "GROUP BY sa.normalizedQuery " +
           "ORDER BY searchCount DESC")
    List<Object[]> findPopularQueriesWithResults(@Param("since") LocalDateTime since, Pageable pageable);

    // No-result searches
    @Query("SELECT sa.normalizedQuery, COUNT(sa) as searchCount " +
           "FROM SearchAnalytics sa " +
           "WHERE sa.resultCount = 0 AND sa.searchDate >= :since " +
           "GROUP BY sa.normalizedQuery " +
           "ORDER BY searchCount DESC")
    List<Object[]> findNoResultQueries(@Param("since") LocalDateTime since, Pageable pageable);

    // Search performance analytics
    @Query("SELECT AVG(sa.executionTimeMs), MAX(sa.executionTimeMs), MIN(sa.executionTimeMs) " +
           "FROM SearchAnalytics sa " +
           "WHERE sa.searchDate >= :since")
    Object[] findSearchPerformanceStats(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(sa) FROM SearchAnalytics sa WHERE sa.executionTimeMs > :threshold AND sa.searchDate >= :since")
    long countSlowSearches(@Param("threshold") Long threshold, @Param("since") LocalDateTime since);

    // Click-through analytics
    @Query("SELECT COUNT(sa) FROM SearchAnalytics sa WHERE sa.hadClicks = true AND sa.searchDate >= :since")
    long countSearchesWithClicks(@Param("since") LocalDateTime since);

    @Query("SELECT AVG(CAST(sa.firstClickPosition AS double)) FROM SearchAnalytics sa " +
           "WHERE sa.hadClicks = true AND sa.firstClickPosition IS NOT NULL AND sa.searchDate >= :since")
    Double findAverageClickPosition(@Param("since") LocalDateTime since);

    // Conversion analytics
    @Query("SELECT COUNT(sa) FROM SearchAnalytics sa WHERE sa.ledToPurchase = true AND sa.searchDate >= :since")
    long countSearchesToPurchase(@Param("since") LocalDateTime since);

    @Query("SELECT (COUNT(sa) * 100.0 / (SELECT COUNT(s) FROM SearchAnalytics s WHERE s.searchDate >= :since)) " +
           "FROM SearchAnalytics sa WHERE sa.ledToPurchase = true AND sa.searchDate >= :since")
    Double findSearchToPurchaseRate(@Param("since") LocalDateTime since);

    // User behavior analytics
    @Query("SELECT sa.userSessionId, COUNT(sa) as searchCount " +
           "FROM SearchAnalytics sa " +
           "WHERE sa.searchDate >= :since AND sa.userSessionId IS NOT NULL " +
           "GROUP BY sa.userSessionId " +
           "HAVING COUNT(sa) > :minSearches " +
           "ORDER BY searchCount DESC")
    List<Object[]> findActiveSearchSessions(@Param("since") LocalDateTime since, 
                                           @Param("minSearches") Long minSearches, 
                                           Pageable pageable);

    // Search trends
    @Query("SELECT DATE(sa.searchDate) as searchDate, COUNT(sa) as searchCount " +
           "FROM SearchAnalytics sa " +
           "WHERE sa.searchDate >= :since " +
           "GROUP BY DATE(sa.searchDate) " +
           "ORDER BY searchDate DESC")
    List<Object[]> findSearchTrends(@Param("since") LocalDateTime since);

    @Query("SELECT HOUR(sa.searchDate) as searchHour, COUNT(sa) as searchCount " +
           "FROM SearchAnalytics sa " +
           "WHERE sa.searchDate >= :since " +
           "GROUP BY HOUR(sa.searchDate) " +
           "ORDER BY searchHour")
    List<Object[]> findSearchPatternsByHour(@Param("since") LocalDateTime since);

    // Filter analytics
    @Query("SELECT sa.appliedFilters, COUNT(sa) as usageCount " +
           "FROM SearchAnalytics sa " +
           "WHERE sa.appliedFilters IS NOT NULL AND sa.searchDate >= :since " +
           "GROUP BY sa.appliedFilters " +
           "ORDER BY usageCount DESC")
    List<Object[]> findPopularFilters(@Param("since") LocalDateTime since, Pageable pageable);

    // Sort criteria analytics
    @Query("SELECT sa.sortCriteria, COUNT(sa) as usageCount " +
           "FROM SearchAnalytics sa " +
           "WHERE sa.sortCriteria IS NOT NULL AND sa.searchDate >= :since " +
           "GROUP BY sa.sortCriteria " +
           "ORDER BY usageCount DESC")
    List<Object[]> findPopularSortCriteria(@Param("since") LocalDateTime since);

    // Search source analytics
    @Query("SELECT sa.searchSource, COUNT(sa) as searchCount " +
           "FROM SearchAnalytics sa " +
           "WHERE sa.searchDate >= :since " +
           "GROUP BY sa.searchSource " +
           "ORDER BY searchCount DESC")
    List<Object[]> findSearchSourceDistribution(@Param("since") LocalDateTime since);

    // Suggestion analytics
    @Query("SELECT COUNT(sa) FROM SearchAnalytics sa " +
           "WHERE sa.suggestedQuery IS NOT NULL AND sa.searchDate >= :since")
    long countSearchesWithSuggestions(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(sa) FROM SearchAnalytics sa " +
           "WHERE sa.suggestionAccepted = true AND sa.searchDate >= :since")
    long countAcceptedSuggestions(@Param("since") LocalDateTime since);

    // Category context analytics
    @Query("SELECT sa.categoryContext, COUNT(sa) as searchCount " +
           "FROM SearchAnalytics sa " +
           "WHERE sa.categoryContext IS NOT NULL AND sa.searchDate >= :since " +
           "GROUP BY sa.categoryContext " +
           "ORDER BY searchCount DESC")
    List<Object[]> findSearchesByCategory(@Param("since") LocalDateTime since);

    // Recent searches by session
    List<SearchAnalytics> findByUserSessionIdOrderBySearchDateDesc(String userSessionId, Pageable pageable);

    // Search effectiveness
    @Query("SELECT sa FROM SearchAnalytics sa " +
           "WHERE sa.searchDate >= :since " +
           "ORDER BY " +
           "CASE WHEN sa.ledToPurchase = true THEN 3 " +
           "     WHEN sa.hadClicks = true THEN 2 " +
           "     WHEN sa.resultCount > 0 THEN 1 " +
           "     ELSE 0 END DESC, " +
           "sa.searchDate DESC")
    Page<SearchAnalytics> findMostEffectiveSearches(@Param("since") LocalDateTime since, Pageable pageable);

    // Failed searches (no results, no suggestions)
    @Query("SELECT sa FROM SearchAnalytics sa " +
           "WHERE sa.resultCount = 0 AND sa.suggestedQuery IS NULL AND sa.searchDate >= :since " +
           "ORDER BY sa.searchDate DESC")
    List<SearchAnalytics> findFailedSearches(@Param("since") LocalDateTime since, Pageable pageable);

    // Search query similarity (for finding related searches)
    @Query("SELECT DISTINCT sa.normalizedQuery " +
           "FROM SearchAnalytics sa " +
           "WHERE sa.normalizedQuery LIKE %:queryPart% " +
           "AND sa.resultCount > 0 " +
           "AND sa.searchDate >= :since " +
           "ORDER BY sa.normalizedQuery")
    List<String> findSimilarQueries(@Param("queryPart") String queryPart, 
                                   @Param("since") LocalDateTime since, 
                                   Pageable pageable);

    // Autocomplete analytics
    @Query("SELECT sa.normalizedQuery, COUNT(sa) as searchCount " +
           "FROM SearchAnalytics sa " +
           "WHERE sa.isAutocomplete = true AND sa.searchDate >= :since " +
           "GROUP BY sa.normalizedQuery " +
           "ORDER BY searchCount DESC")
    List<Object[]> findPopularAutocompleteQueries(@Param("since") LocalDateTime since, Pageable pageable);

    // Search volume by time period
    @Query("SELECT COUNT(sa) FROM SearchAnalytics sa WHERE sa.searchDate >= :since")
    long countSearchesSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(sa) FROM SearchAnalytics sa " +
           "WHERE sa.searchDate >= :start AND sa.searchDate < :end")
    long countSearchesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // User engagement metrics
    @Query("SELECT AVG(CAST(sa.totalClicks AS double)) FROM SearchAnalytics sa " +
           "WHERE sa.totalClicks > 0 AND sa.searchDate >= :since")
    Double findAverageClicksPerSearch(@Param("since") LocalDateTime since);

    // Search result quality metrics
    @Query("SELECT AVG(CAST(sa.resultCount AS double)) FROM SearchAnalytics sa WHERE sa.searchDate >= :since")
    Double findAverageResultCount(@Param("since") LocalDateTime since);

    @Query("SELECT sa.normalizedQuery, AVG(CAST(sa.resultCount AS double)) as avgResults " +
           "FROM SearchAnalytics sa " +
           "WHERE sa.searchDate >= :since " +
           "GROUP BY sa.normalizedQuery " +
           "HAVING COUNT(sa) >= :minOccurrences " +
           "ORDER BY avgResults DESC")
    List<Object[]> findQueriesWithMostResults(@Param("since") LocalDateTime since, 
                                             @Param("minOccurrences") Long minOccurrences, 
                                             Pageable pageable);
}
