package org.de013.productcatalog.service;

import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.product.ProductSummaryDto;
import org.de013.productcatalog.dto.search.ProductFilterDto;
import org.de013.productcatalog.dto.search.ProductSearchDto;
import org.de013.productcatalog.dto.search.SearchResultDto;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface SearchService {

    // Main Search Operations
    SearchResultDto searchProducts(ProductSearchDto searchDto);
    
    PageResponse<ProductSummaryDto> simpleSearch(String query, Pageable pageable);
    
    PageResponse<ProductSummaryDto> fullTextSearch(String query, Pageable pageable);
    
    PageResponse<ProductSummaryDto> advancedSearch(ProductSearchDto searchDto);

    // Filter Operations
    ProductFilterDto getAvailableFilters();
    
    ProductFilterDto getAvailableFilters(String query);
    
    ProductFilterDto getAvailableFilters(List<Long> categoryIds);
    
    ProductFilterDto getFiltersForSearchResults(ProductSearchDto searchDto);

    // Category-based Search
    PageResponse<ProductSummaryDto> searchInCategory(Long categoryId, String query, Pageable pageable);

    PageResponse<ProductSummaryDto> searchInCategories(List<Long> categoryIds, String query, Pageable pageable);

    // Brand-based Search
    PageResponse<ProductSummaryDto> searchByBrand(String brand, String query, Pageable pageable);

    // Price-based Search
    PageResponse<ProductSummaryDto> searchByPriceRange(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    // Search Suggestions
    List<String> getSearchSuggestions(String query);

    List<String> getSearchSuggestions(String query, int limit);

    // Auto-complete
    List<String> autocompleteProductNames(String query);

    List<String> autocompleteProductNames(String query, int limit);

    // Search Analytics
    void recordSearch(String query, long resultCount);

    void recordSearch(ProductSearchDto searchDto, long resultCount);

    // Search Results Enhancement
    SearchResultDto enhanceSearchResults(SearchResultDto results);

    // Faceted Search
    SearchResultDto facetedSearch(ProductSearchDto searchDto);

    // Search Performance
    SearchResultDto searchWithPerformanceTracking(ProductSearchDto searchDto);

    // Search Filters Validation
    boolean isValidSearchQuery(String query);

    boolean isValidPriceRange(BigDecimal minPrice, BigDecimal maxPrice);

    boolean isValidRatingRange(Double minRating, Double maxRating);

    ProductSearchDto sanitizeSearchDto(ProductSearchDto searchDto);

    // Search Results Enhancement
    SearchResultDto addRecommendations(SearchResultDto results);

    SearchResultDto addRelatedProducts(SearchResultDto results);

    SearchResultDto addPopularProducts(SearchResultDto results);

    // Search Cache Operations
    void clearSearchCache();

    void clearSearchCache(String query);

    void warmupSearchCache();
}
