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
    
    PageResponse<ProductSummaryDto> searchInCategoryTree(Long rootCategoryId, String query, Pageable pageable);

    // Brand-based Search
    PageResponse<ProductSummaryDto> searchByBrand(String brand, String query, Pageable pageable);
    
    PageResponse<ProductSummaryDto> searchByBrands(List<String> brands, String query, Pageable pageable);
    
    List<String> suggestBrands(String query);

    // Price-based Search
    PageResponse<ProductSummaryDto> searchByPriceRange(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);
    
    PageResponse<ProductSummaryDto> searchByPriceRange(String query, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    // Rating-based Search
    PageResponse<ProductSummaryDto> searchByMinRating(Double minRating, Pageable pageable);
    
    PageResponse<ProductSummaryDto> searchByMinRating(String query, Double minRating, Pageable pageable);

    // Feature-based Search
    PageResponse<ProductSummaryDto> searchFeaturedProducts(String query, Pageable pageable);
    
    PageResponse<ProductSummaryDto> searchInStockProducts(String query, Pageable pageable);
    
    PageResponse<ProductSummaryDto> searchOnSaleProducts(String query, Pageable pageable);
    
    PageResponse<ProductSummaryDto> searchDigitalProducts(String query, Pageable pageable);

    // Search Suggestions
    List<String> getSearchSuggestions(String query);
    
    List<String> getSearchSuggestions(String query, int limit);
    
    List<String> getPopularSearchTerms();
    
    List<String> getPopularSearchTerms(int limit);

    // Auto-complete
    List<String> autocompleteProductNames(String query);
    
    List<String> autocompleteProductNames(String query, int limit);
    
    List<String> autocompleteBrands(String query);
    
    List<String> autocompleteCategories(String query);

    // Search Analytics
    void recordSearch(String query, long resultCount);
    
    void recordSearch(ProductSearchDto searchDto, long resultCount);
    
    List<String> getTrendingSearches();
    
    List<String> getTrendingSearches(int limit);

    // Search Optimization
    List<String> getDidYouMeanSuggestions(String query);
    
    List<String> getRelatedSearches(String query);
    
    SearchResultDto enhanceSearchResults(SearchResultDto results);

    // Faceted Search
    SearchResultDto facetedSearch(ProductSearchDto searchDto);
    
    List<ProductFilterDto.CategoryFilter> getCategoryFacets(String query);
    
    List<ProductFilterDto.BrandFilter> getBrandFacets(String query);
    
    List<ProductFilterDto.PriceRange.PriceRangeOption> getPriceFacets(String query);

    // Search Indexing (for future Elasticsearch integration)
    void indexProduct(Long productId);
    
    void indexProducts(List<Long> productIds);
    
    void reindexAllProducts();
    
    void removeFromIndex(Long productId);

    // Search Performance
    SearchResultDto searchWithPerformanceTracking(ProductSearchDto searchDto);
    
    long getSearchExecutionTime(ProductSearchDto searchDto);
    
    void optimizeSearchIndex();

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

    // Search Configuration
    void updateSearchConfiguration(String key, Object value);
    
    Object getSearchConfiguration(String key);
    
    void resetSearchConfiguration();
}
