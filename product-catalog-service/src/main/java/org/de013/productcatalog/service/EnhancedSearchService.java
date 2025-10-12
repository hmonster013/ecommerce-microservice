package org.de013.productcatalog.service;

import org.de013.productcatalog.dto.search.*;
import org.de013.productcatalog.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Enhanced search service with advanced features and analytics.
 */
public interface EnhancedSearchService {

    // Core search functionality
    SearchResultDto search(ProductSearchDto searchDto);
    SearchResultDto search(ProductSearchDto searchDto, String sessionId, String userAgent, String ipAddress);
    
    // Advanced search features
    Page<Product> searchProducts(ProductSearchDto searchDto, Pageable pageable);
    List<Product> findSimilarProducts(Long productId, int limit);
    List<Product> findTrendingProducts(int limit);
    List<Product> findRecommendedProducts(String sessionId, int limit);
    
    // Search suggestions and autocomplete
    List<String> getSearchSuggestions(String query, int limit);
    List<String> getPopularSearches(int limit);
    List<String> getAutocompleteSuggestions(String partialQuery, int limit);
    
    // Search analytics
    void trackSearch(String query, Long resultCount, Long executionTime, String sessionId,
                    String userAgent, String ipAddress, ProductSearchDto searchDto);
    void trackSearchClick(String sessionId, String query, int position, Long productId);
    void trackSearchToPurchase(String sessionId, String query, Long productId);

    // Search optimization
    List<String> getSuggestedQueriesForNoResults(String originalQuery);
    boolean shouldShowDidYouMean(String query, Long resultCount);
    List<String> getRelatedSearches(String query, int limit);

    // Cache management
    void clearSearchCache();
    void clearSearchCache(String cacheKey);
    void warmupSearchCache();
}
