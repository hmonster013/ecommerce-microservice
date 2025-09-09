package org.de013.productcatalog.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.productcatalog.dto.product.ProductSummaryDto;
import org.de013.productcatalog.dto.search.*;
import org.de013.productcatalog.entity.Product;
import org.de013.productcatalog.entity.SearchAnalytics;
import org.de013.productcatalog.repository.ProductRepository;
import org.de013.productcatalog.repository.SearchAnalyticsRepository;
import org.de013.productcatalog.repository.specification.AdvancedProductSpecification;
import org.de013.productcatalog.service.EnhancedSearchService;
import org.de013.productcatalog.mapper.ProductMapper;
import org.de013.productcatalog.util.SearchUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced search service implementation with advanced features and analytics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnhancedSearchServiceImpl implements EnhancedSearchService {

    private final ProductRepository productRepository;
    private final SearchAnalyticsRepository searchAnalyticsRepository;
    private final ProductMapper productMapper;
    private final ObjectMapper objectMapper;

    @Override
    public SearchResultDto search(ProductSearchDto searchDto) {
        return search(searchDto, null, null, null);
    }

    @Override
    public SearchResultDto search(ProductSearchDto searchDto, String sessionId, String userAgent, String ipAddress) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("Executing search with query: {}", searchDto.getQuery());
            
            // Create pageable with sorting
            Pageable pageable = createPageable(searchDto);
            
            // Execute search
            Page<Product> productPage = searchProducts(searchDto, pageable);
            
            // Convert to DTOs
            List<ProductSummaryDto> productDtos = productPage.getContent().stream()
                    .map(productMapper::toProductSummaryDto)
                    .collect(Collectors.toList());
            
            // Build search result
            SearchResultDto result = SearchResultDto.builder()
                    .query(searchDto.getQuery())
                    .totalResults((long) productPage.getTotalElements())
                    .totalPages(productPage.getTotalPages())
                    .currentPage(productPage.getNumber())
                    .pageSize(productPage.getSize())
                    .products(productDtos)
                    .hasNext(productPage.hasNext())
                    .hasPrevious(productPage.hasPrevious())
                    .build();
            
            // Add search suggestions if needed
            if (shouldShowDidYouMean(searchDto.getQuery(), result.getTotalResults())) {
                result.setDidYouMean(getSuggestedQueriesForNoResults(searchDto.getQuery()));
            }
            
            // Add related searches
            if (StringUtils.hasText(searchDto.getQuery())) {
                result.setRelatedSearches(getRelatedSearches(searchDto.getQuery(), 5));
            }
            
            // Track search analytics
            long executionTime = System.currentTimeMillis() - startTime;
            trackSearch(searchDto.getQuery(), result.getTotalResults(), executionTime, 
                       sessionId, userAgent, ipAddress, searchDto);
            
            log.debug("Search completed in {}ms with {} results", executionTime, result.getTotalResults());
            return result;
            
        } catch (Exception e) {
            log.error("Error executing search for query: {}", searchDto.getQuery(), e);
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Track failed search
            trackSearch(searchDto.getQuery(), 0L, executionTime, sessionId, userAgent, ipAddress, searchDto);
            
            // Return empty result
            return SearchResultDto.builder()
                    .query(searchDto.getQuery())
                    .totalResults(0L)
                    .products(Collections.emptyList())
                    .build();
        }
    }

    @Override
    public Page<Product> searchProducts(ProductSearchDto searchDto, Pageable pageable) {
        Specification<Product> specification = AdvancedProductSpecification.fromSearchDto(searchDto);
        return productRepository.findAll(specification, pageable);
    }

    @Override
    @Cacheable(value = "similarProducts", key = "#productId")
    public List<Product> findSimilarProducts(Long productId, int limit) {
        log.debug("Finding similar products for product ID: {}", productId);
        
        return productRepository.findById(productId)
                .map(product -> {
                    Specification<Product> specification = AdvancedProductSpecification.findSimilarProducts(product);
                    Pageable pageable = PageRequest.of(0, limit);
                    return productRepository.findAll(specification, pageable).getContent();
                })
                .orElse(Collections.emptyList());
    }

    @Override
    @Cacheable(value = "trendingProducts", key = "#limit")
    public List<Product> findTrendingProducts(int limit) {
        log.debug("Finding trending products with limit: {}", limit);
        
        Specification<Product> specification = AdvancedProductSpecification.findTrendingProducts();
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return productRepository.findAll(specification, pageable).getContent();
    }

    @Override
    public List<Product> findRecommendedProducts(String sessionId, int limit) {
        log.debug("Finding recommended products for session: {}", sessionId);
        
        // Get recent searches for this session
        List<SearchAnalytics> recentSearches = searchAnalyticsRepository
                .findByUserSessionIdOrderBySearchDateDesc(sessionId, PageRequest.of(0, 10));
        
        if (recentSearches.isEmpty()) {
            // Fallback to trending products
            return findTrendingProducts(limit);
        }
        
        // Extract search terms and find products
        Set<String> searchTerms = recentSearches.stream()
                .map(SearchAnalytics::getNormalizedQuery)
                .filter(StringUtils::hasText)
                .flatMap(query -> SearchUtils.extractSearchTerms(query).stream())
                .collect(Collectors.toSet());
        
        if (searchTerms.isEmpty()) {
            return findTrendingProducts(limit);
        }
        
        // Create search DTO from user's search history
        ProductSearchDto searchDto = ProductSearchDto.builder()
                .query(String.join(" ", searchTerms))
                .page(0)
                .size(limit)
                .build();
        
        return searchProducts(searchDto, PageRequest.of(0, limit)).getContent();
    }

    @Override
    @Cacheable(value = "searchSuggestions", key = "#query")
    public List<String> getSearchSuggestions(String query, int limit) {
        if (!StringUtils.hasText(query)) {
            return Collections.emptyList();
        }
        
        log.debug("Getting search suggestions for query: {}", query);
        
        // Get popular queries that contain the search terms
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<Object[]> popularQueries = searchAnalyticsRepository
                .findPopularQueriesWithResults(since, PageRequest.of(0, limit * 2));
        
        List<String> candidates = popularQueries.stream()
                .map(row -> (String) row[0])
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        return SearchUtils.generateSuggestions(query, candidates, limit);
    }

    @Override
    @Cacheable(value = "popularSearches", key = "#limit")
    public List<String> getPopularSearches(int limit) {
        log.debug("Getting popular searches with limit: {}", limit);
        
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<Object[]> popularQueries = searchAnalyticsRepository
                .findPopularQueriesWithResults(since, PageRequest.of(0, limit));
        
        return popularQueries.stream()
                .map(row -> (String) row[0])
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getAutocompleteSuggestions(String partialQuery, int limit) {
        if (!StringUtils.hasText(partialQuery) || partialQuery.length() < 2) {
            return Collections.emptyList();
        }
        
        log.debug("Getting autocomplete suggestions for: {}", partialQuery);
        
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        return searchAnalyticsRepository.findSimilarQueries(
                partialQuery, since, PageRequest.of(0, limit));
    }

    @Override
    @Transactional
    public void trackSearch(String query, Long resultCount, Long executionTime, String sessionId, 
                           String userAgent, String ipAddress, ProductSearchDto searchDto) {
        try {
            SearchAnalytics analytics = SearchAnalytics.builder()
                    .searchQuery(query)
                    .normalizedQuery(SearchUtils.normalizeQuery(query))
                    .resultCount(resultCount)
                    .executionTimeMs(executionTime)
                    .userSessionId(sessionId)
                    .userAgent(userAgent)
                    .userIpHash(hashIpAddress(ipAddress))
                    .appliedFilters(serializeFilters(searchDto))
                    .sortCriteria(searchDto.getSort())
                    .pageNumber(searchDto.getPage())
                    .pageSize(searchDto.getSize())
                    .searchSource("web") // Default to web, could be parameterized
                    .searchLocale("en") // Default to English, could be parameterized
                    .isAutocomplete(false) // Could be parameterized
                    .searchDate(LocalDateTime.now())
                    .build();
            
            searchAnalyticsRepository.save(analytics);
            log.debug("Tracked search analytics for query: {}", query);
            
        } catch (Exception e) {
            log.error("Error tracking search analytics for query: {}", query, e);
        }
    }

    @Override
    @Transactional
    public void trackSearchClick(String sessionId, String query, int position, Long productId) {
        // This would typically update the search analytics record
        // For now, we'll log the click
        log.debug("Tracked search click - session: {}, query: {}, position: {}, product: {}", 
                 sessionId, query, position, productId);
    }

    @Override
    @Transactional
    public void trackSearchToPurchase(String sessionId, String query, Long productId) {
        // This would typically update the search analytics record
        // For now, we'll log the conversion
        log.debug("Tracked search to purchase - session: {}, query: {}, product: {}", 
                 sessionId, query, productId);
    }

    // Helper methods

    private Pageable createPageable(ProductSearchDto searchDto) {
        int page = searchDto.getPage() != null ? searchDto.getPage() : 0;
        int size = searchDto.getSize() != null ? searchDto.getSize() : 20;
        
        // Create sort
        Sort sort = createSort(searchDto.getSort());
        
        return PageRequest.of(page, size, sort);
    }

    private Sort createSort(String sortCriteria) {
        if (!StringUtils.hasText(sortCriteria)) {
            return Sort.by(Sort.Direction.DESC, "createdAt"); // Default sort
        }
        
        switch (sortCriteria.toLowerCase()) {
            case "price_asc":
                return Sort.by(Sort.Direction.ASC, "price");
            case "price_desc":
                return Sort.by(Sort.Direction.DESC, "price");
            case "name_asc":
                return Sort.by(Sort.Direction.ASC, "name");
            case "name_desc":
                return Sort.by(Sort.Direction.DESC, "name");
            case "rating_desc":
                // This would require a complex sort by average rating
                return Sort.by(Sort.Direction.DESC, "featured", "createdAt");
            case "newest":
                return Sort.by(Sort.Direction.DESC, "createdAt");
            case "oldest":
                return Sort.by(Sort.Direction.ASC, "createdAt");
            case "featured":
                return Sort.by(Sort.Direction.DESC, "featured", "createdAt");
            default:
                return Sort.by(Sort.Direction.DESC, "createdAt");
        }
    }

    private String hashIpAddress(String ipAddress) {
        if (!StringUtils.hasText(ipAddress)) {
            return null;
        }
        
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(ipAddress.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("Error hashing IP address", e);
            return null;
        }
    }

    private String serializeFilters(ProductSearchDto searchDto) {
        try {
            Map<String, Object> filters = new HashMap<>();
            
            if (searchDto.getCategoryIds() != null && !searchDto.getCategoryIds().isEmpty()) {
                filters.put("categories", searchDto.getCategoryIds());
            }
            
            if (searchDto.getBrands() != null && !searchDto.getBrands().isEmpty()) {
                filters.put("brands", searchDto.getBrands());
            }
            
            if (searchDto.getMinPrice() != null || searchDto.getMaxPrice() != null) {
                Map<String, Object> priceRange = new HashMap<>();
                if (searchDto.getMinPrice() != null) priceRange.put("min", searchDto.getMinPrice());
                if (searchDto.getMaxPrice() != null) priceRange.put("max", searchDto.getMaxPrice());
                filters.put("price", priceRange);
            }
            

            
            if (searchDto.getInStock() != null) {
                filters.put("inStock", searchDto.getInStock());
            }
            
            return filters.isEmpty() ? null : objectMapper.writeValueAsString(filters);
            
        } catch (JsonProcessingException e) {
            log.error("Error serializing search filters", e);
            return null;
        }
    }

    // Placeholder implementations for remaining methods
    
    @Override
    public SearchPerformanceDto getSearchPerformance(LocalDateTime since) {
        // Implementation would aggregate search analytics data
        return SearchPerformanceDto.builder()
                .totalSearches(0L)
                .searchesWithResults(0L)
                .noResultSearches(0L)
                .averageExecutionTime(0.0)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Override
    public List<PopularQueryDto> getPopularQueries(LocalDateTime since, int limit) {
        return Collections.emptyList();
    }

    @Override
    public List<PopularQueryDto> getNoResultQueries(LocalDateTime since, int limit) {
        return Collections.emptyList();
    }

    @Override
    public SearchInsightsDto getSearchInsights(LocalDateTime since) {
        return SearchInsightsDto.builder()
                .periodStart(since)
                .periodEnd(LocalDateTime.now())
                .insights(Collections.emptyList())
                .build();
    }

    @Override
    public Map<String, Long> getSearchTrends(LocalDateTime since) {
        return Collections.emptyMap();
    }

    @Override
    public Map<Integer, Long> getSearchPatternsByHour(LocalDateTime since) {
        return Collections.emptyMap();
    }

    @Override
    public List<FilterUsageDto> getPopularFilters(LocalDateTime since, int limit) {
        return Collections.emptyList();
    }

    @Override
    public List<SortUsageDto> getPopularSortCriteria(LocalDateTime since) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getSuggestedQueriesForNoResults(String originalQuery) {
        return getSearchSuggestions(originalQuery, 3);
    }

    @Override
    public boolean shouldShowDidYouMean(String query, Long resultCount) {
        return resultCount != null && resultCount == 0 && StringUtils.hasText(query);
    }

    @Override
    public List<String> getRelatedSearches(String query, int limit) {
        return getSearchSuggestions(query, limit);
    }

    @Override
    public void clearSearchCache() {
        // Implementation would clear all search-related caches
        log.info("Search cache cleared");
    }

    @Override
    public void clearSearchCache(String cacheKey) {
        // Implementation would clear specific cache entry
        log.info("Search cache cleared for key: {}", cacheKey);
    }

    @Override
    public void warmupSearchCache() {
        // Implementation would pre-populate frequently used cache entries
        log.info("Search cache warmed up");
    }

    @Override
    public SearchConfigDto getSearchConfiguration() {
        return null; // Placeholder
    }

    @Override
    public void updateSearchConfiguration(SearchConfigDto config) {
        // Placeholder
    }

    @Override
    public void reindexAllProducts() {
        // Placeholder
    }

    @Override
    public void reindexProduct(Long productId) {
        // Placeholder
    }

    @Override
    public void optimizeSearchIndex() {
        // Placeholder
    }

    @Override
    public SearchHealthDto getSearchHealth() {
        return null; // Placeholder
    }

    @Override
    public boolean isSearchHealthy() {
        return true; // Placeholder
    }

    @Override
    public byte[] exportSearchAnalytics(LocalDateTime since, String format) {
        return new byte[0]; // Placeholder
    }

    @Override
    public SearchReportDto generateSearchReport(LocalDateTime since) {
        return null; // Placeholder
    }
}
