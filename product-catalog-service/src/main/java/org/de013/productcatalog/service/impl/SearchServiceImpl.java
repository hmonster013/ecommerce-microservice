package org.de013.productcatalog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.product.ProductSummaryDto;
import org.de013.productcatalog.dto.search.ProductFilterDto;
import org.de013.productcatalog.dto.search.ProductSearchDto;
import org.de013.productcatalog.dto.search.SearchResultDto;
import org.de013.productcatalog.entity.Product;
import org.de013.productcatalog.entity.enums.ProductStatus;
import org.de013.productcatalog.repository.CategoryRepository;
import org.de013.productcatalog.repository.ProductRepository;
import org.de013.productcatalog.repository.specification.ProductSpecification;
import org.de013.productcatalog.service.SearchService;
import org.de013.productcatalog.mapper.ProductMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchServiceImpl implements SearchService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SearchServiceImpl.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Override
    @Cacheable(value = "search_results", key = "#searchDto.hashCode()")
    public SearchResultDto searchProducts(ProductSearchDto searchDto) {
        log.info("Searching products with criteria: {}", searchDto);
        
        long startTime = System.currentTimeMillis();
        
        // Build specification from search criteria
        Specification<Product> spec = ProductSpecification.buildFromSearchDto(searchDto);
        
        // Create pageable with sorting
        Pageable pageable = createPageable(searchDto);
        
        // Execute search
        Page<Product> products = productRepository.findAll(spec, pageable);
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        // Map to DTOs
        List<ProductSummaryDto> productDtos = products.getContent().stream()
                .map(productMapper::toProductSummaryDto)
                .collect(Collectors.toList());
        
        // Build search result
        SearchResultDto result = SearchResultDto.builder()
                .query(searchDto.getQuery())
                .totalResults(products.getTotalElements())
                .executionTimeMs(executionTime)
                .products(productDtos)
                .availableFilters(getFiltersForSearchResults(searchDto))
                .metadata(createSearchMetadata(searchDto, products))
                .build();
        
        // Enhance results
        result = enhanceSearchResults(result);
        
        // Record search analytics
        recordSearch(searchDto, products.getTotalElements());
        
        log.info("Search completed in {}ms, found {} results", executionTime, products.getTotalElements());
        return result;
    }

    @Override
    @Cacheable(value = "simple_search", key = "#query + '_' + #pageable.hashCode()")
    public PageResponse<ProductSummaryDto> simpleSearch(String query, Pageable pageable) {
        log.debug("Simple search for query: {}", query);
        
        if (!StringUtils.hasText(query)) {
            return getEmptyPageResponse();
        }
        
        Page<Product> products = productRepository.searchByQuery(query, ProductStatus.ACTIVE.name(), pageable);
        return mapToPageResponse(products);
    }

    @Override
    @Cacheable(value = "fulltext_search", key = "#query + '_' + #pageable.hashCode()")
    public PageResponse<ProductSummaryDto> fullTextSearch(String query, Pageable pageable) {
        log.debug("Full-text search for query: {}", query);
        
        if (!StringUtils.hasText(query)) {
            return getEmptyPageResponse();
        }
        
        Page<Product> products = productRepository.fullTextSearch(query, ProductStatus.ACTIVE.name(), pageable);
        return mapToPageResponse(products);
    }

    @Override
    public PageResponse<ProductSummaryDto> advancedSearch(ProductSearchDto searchDto) {
        log.debug("Advanced search with criteria: {}", searchDto);
        
        SearchResultDto result = searchProducts(searchDto);
        
        return PageResponse.<ProductSummaryDto>builder()
                .content(result.getProducts())
                .page(searchDto.getPage())
                .size(searchDto.getSize())
                .totalElements(result.getTotalResults())
                .totalPages((int) Math.ceil((double) result.getTotalResults() / searchDto.getSize()))
                .first(searchDto.getPage() == 0)
                .last(searchDto.getPage() >= Math.ceil((double) result.getTotalResults() / searchDto.getSize()) - 1)
                .empty(result.getProducts().isEmpty())
                .build();
    }

    @Override
    @Cacheable(value = "search_filters", key = "'all'")
    public ProductFilterDto getAvailableFilters() {
        log.debug("Getting all available filters");
        
        return ProductFilterDto.builder()
                .categories(getCategoryFilters())
                .brands(getBrandFilters())
                .priceRange(getPriceRangeFilters())
                .ratings(getRatingFilters())
                .features(getFeatureFilters())
                .build();
    }

    @Override
    @Cacheable(value = "search_filters", key = "#query")
    public ProductFilterDto getAvailableFilters(String query) {
        log.debug("Getting available filters for query: {}", query);
        
        if (!StringUtils.hasText(query)) {
            return getAvailableFilters();
        }
        
        // Get filters based on search results
        ProductSearchDto searchDto = ProductSearchDto.builder()
                .query(query)
                .page(0)
                .size(1000) // Large size to get all results for filtering
                .build();
        
        return getFiltersForSearchResults(searchDto);
    }

    @Override
    public ProductFilterDto getAvailableFilters(List<Long> categoryIds) {
        log.debug("Getting available filters for categories: {}", categoryIds);
        
        ProductSearchDto searchDto = ProductSearchDto.builder()
                .categoryIds(categoryIds)
                .page(0)
                .size(1000)
                .build();
        
        return getFiltersForSearchResults(searchDto);
    }

    @Override
    public ProductFilterDto getFiltersForSearchResults(ProductSearchDto searchDto) {
        log.debug("Getting filters for search results");
        
        // This would typically analyze the search results to provide relevant filters
        // For now, return basic filters
        return getAvailableFilters();
    }

    @Override
    public PageResponse<ProductSummaryDto> searchInCategory(Long categoryId, String query, Pageable pageable) {
        log.debug("Searching in category {} with query: {}", categoryId, query);
        
        ProductSearchDto searchDto = ProductSearchDto.builder()
                .query(query)
                .categoryIds(List.of(categoryId))
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .build();
        
        return advancedSearch(searchDto);
    }

    @Override
    public PageResponse<ProductSummaryDto> searchInCategories(List<Long> categoryIds, String query, Pageable pageable) {
        log.debug("Searching in categories {} with query: {}", categoryIds, query);
        
        ProductSearchDto searchDto = ProductSearchDto.builder()
                .query(query)
                .categoryIds(categoryIds)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .build();
        
        return advancedSearch(searchDto);
    }

    @Override
    public PageResponse<ProductSummaryDto> searchByBrand(String brand, String query, Pageable pageable) {
        log.debug("Searching by brand {} with query: {}", brand, query);
        
        ProductSearchDto searchDto = ProductSearchDto.builder()
                .query(query)
                .brands(List.of(brand))
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .build();
        
        return advancedSearch(searchDto);
    }

    @Override
    public PageResponse<ProductSummaryDto> searchByPriceRange(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        log.debug("Searching by price range: {} - {}", minPrice, maxPrice);
        
        ProductSearchDto searchDto = ProductSearchDto.builder()
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .build();
        
        return advancedSearch(searchDto);
    }

    @Override
    public List<String> getSearchSuggestions(String query) {
        return getSearchSuggestions(query, 10);
    }

    @Override
    @Cacheable(value = "search_suggestions", key = "#query + '_' + #limit")
    public List<String> getSearchSuggestions(String query, int limit) {
        log.debug("Getting search suggestions for query: {} with limit: {}", query, limit);
        
        if (!StringUtils.hasText(query) || query.length() < 2) {
            return List.of();
        }
        
        // This would typically use a dedicated search suggestion service
        // For now, return basic suggestions based on product names
        return autocompleteProductNames(query, limit);
    }

    @Override
    @Cacheable(value = "autocomplete", key = "'products_' + #query + '_' + #limit")
    public List<String> autocompleteProductNames(String query, int limit) {
        log.debug("Autocompleting product names for query: {} with limit: {}", query, limit);
        
        if (!StringUtils.hasText(query) || query.length() < 2) {
            return List.of();
        }
        
        // Simple implementation - would be enhanced with proper search engine
        Pageable pageable = PageRequest.of(0, limit);
        Page<Product> products = productRepository.searchByQuery(query, ProductStatus.ACTIVE.name(), pageable);
        
        return products.getContent().stream()
                .map(Product::getName)
                .distinct()
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public void recordSearch(String query, long resultCount) {
        log.debug("Recording search: query='{}', results={}", query, resultCount);
        // Implementation would store search analytics
    }

    @Override
    public void recordSearch(ProductSearchDto searchDto, long resultCount) {
        log.debug("Recording search: criteria='{}', results={}", searchDto, resultCount);
        // Implementation would store search analytics
    }

    @Override
    public SearchResultDto enhanceSearchResults(SearchResultDto results) {
        log.debug("Enhancing search results");
        
        // Add suggestions if no results
        if (results.getProducts().isEmpty() && StringUtils.hasText(results.getQuery())) {
            results.setDidYouMean(getDidYouMeanSuggestions(results.getQuery()));
            results.setSuggestions(getSearchSuggestions(results.getQuery()));
        }
        
        // Add related searches
        if (StringUtils.hasText(results.getQuery())) {
            results.setRelatedSearches(getRelatedSearches(results.getQuery()));
        }
        
        return results;
    }

    // Helper methods
    private Pageable createPageable(ProductSearchDto searchDto) {
        Sort sort = createSort(searchDto.getEffectiveSortBy(), searchDto.getEffectiveSortDirection());
        return PageRequest.of(searchDto.getPage(), searchDto.getSize(), sort);
    }

    private Sort createSort(String sortBy, String direction) {
        Sort.Direction sortDirection = "DESC".equalsIgnoreCase(direction) ? 
                Sort.Direction.DESC : Sort.Direction.ASC;
        
        return switch (sortBy.toLowerCase()) {
            case "name" -> Sort.by(sortDirection, "name");
            case "price" -> Sort.by(sortDirection, "price");
            case "created" -> Sort.by(sortDirection, "createdAt");
            case "updated" -> Sort.by(sortDirection, "updatedAt");
            case "brand" -> Sort.by(sortDirection, "brand");
            case "relevance" -> Sort.by(Sort.Direction.DESC, "name"); // Simplified relevance
            default -> Sort.by(sortDirection, "name");
        };
    }

    private SearchResultDto.SearchMetadata createSearchMetadata(ProductSearchDto searchDto, Page<Product> products) {
        return SearchResultDto.SearchMetadata.builder()
                .searchType("specification")
                .sortBy(searchDto.getEffectiveSortBy())
                .sortDirection(searchDto.getEffectiveSortDirection())
                .page(searchDto.getPage())
                .size(searchDto.getSize())
                .hasMore(!products.isLast())
                .build();
    }

    private PageResponse<ProductSummaryDto> mapToPageResponse(Page<Product> products) {
        List<ProductSummaryDto> content = products.getContent().stream()
                .map(productMapper::toProductSummaryDto)
                .collect(Collectors.toList());
        
        return PageResponse.<ProductSummaryDto>builder()
                .content(content)
                .page(products.getNumber())
                .size(products.getSize())
                .totalElements(products.getTotalElements())
                .totalPages(products.getTotalPages())
                .first(products.isFirst())
                .last(products.isLast())
                .empty(products.isEmpty())
                .build();
    }

    private PageResponse<ProductSummaryDto> getEmptyPageResponse() {
        return PageResponse.<ProductSummaryDto>builder()
                .content(List.of())
                .page(0)
                .size(0)
                .totalElements(0L)
                .totalPages(0)
                .first(true)
                .last(true)
                .empty(true)
                .build();
    }

    // Filter helper methods (simplified implementations)
    private List<ProductFilterDto.CategoryFilter> getCategoryFilters() {
        // Implementation would build category filters from database
        return List.of();
    }

    private List<ProductFilterDto.BrandFilter> getBrandFilters() {
        // Implementation would build brand filters from database
        return List.of();
    }

    private ProductFilterDto.PriceRange getPriceRangeFilters() {
        // Implementation would build price range filters from database
        return ProductFilterDto.PriceRange.builder().build();
    }

    private List<ProductFilterDto.RatingFilter> getRatingFilters() {
        // Implementation would build rating filters
        return List.of();
    }

    private List<ProductFilterDto.FeatureFilter> getFeatureFilters() {
        // Implementation would build feature filters
        return List.of();
    }

    // Placeholder implementations for remaining methods
    @Override public PageResponse<ProductSummaryDto> searchInCategoryTree(Long rootCategoryId, String query, Pageable pageable) { return null; }
    @Override public PageResponse<ProductSummaryDto> searchByBrands(List<String> brands, String query, Pageable pageable) { return null; }
    @Override public List<String> suggestBrands(String query) { return List.of(); }
    @Override public PageResponse<ProductSummaryDto> searchByPriceRange(String query, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) { return null; }
    @Override public PageResponse<ProductSummaryDto> searchByMinRating(Double minRating, Pageable pageable) { return null; }
    @Override public PageResponse<ProductSummaryDto> searchByMinRating(String query, Double minRating, Pageable pageable) { return null; }
    @Override public PageResponse<ProductSummaryDto> searchFeaturedProducts(String query, Pageable pageable) { return null; }
    @Override public PageResponse<ProductSummaryDto> searchInStockProducts(String query, Pageable pageable) { return null; }
    @Override public PageResponse<ProductSummaryDto> searchOnSaleProducts(String query, Pageable pageable) { return null; }
    @Override public PageResponse<ProductSummaryDto> searchDigitalProducts(String query, Pageable pageable) { return null; }
    @Override public List<String> getPopularSearchTerms() { return List.of(); }
    @Override public List<String> getPopularSearchTerms(int limit) { return List.of(); }
    @Override public List<String> autocompleteProductNames(String query) { return autocompleteProductNames(query, 10); }
    @Override public List<String> autocompleteBrands(String query) { return List.of(); }
    @Override public List<String> autocompleteCategories(String query) { return List.of(); }
    @Override public List<String> getTrendingSearches() { return List.of(); }
    @Override public List<String> getTrendingSearches(int limit) { return List.of(); }
    @Override public List<String> getDidYouMeanSuggestions(String query) { return List.of(); }
    @Override public List<String> getRelatedSearches(String query) { return List.of(); }
    @Override public SearchResultDto facetedSearch(ProductSearchDto searchDto) { return searchProducts(searchDto); }
    @Override public List<ProductFilterDto.CategoryFilter> getCategoryFacets(String query) { return List.of(); }
    @Override public List<ProductFilterDto.BrandFilter> getBrandFacets(String query) { return List.of(); }
    @Override public List<ProductFilterDto.PriceRange.PriceRangeOption> getPriceFacets(String query) { return List.of(); }
    @Override public void indexProduct(Long productId) { }
    @Override public void indexProducts(List<Long> productIds) { }
    @Override public void reindexAllProducts() { }
    @Override public void removeFromIndex(Long productId) { }
    @Override public SearchResultDto searchWithPerformanceTracking(ProductSearchDto searchDto) { return searchProducts(searchDto); }
    @Override public long getSearchExecutionTime(ProductSearchDto searchDto) { return 0; }
    @Override public void optimizeSearchIndex() { }
    @Override public boolean isValidSearchQuery(String query) { return StringUtils.hasText(query); }
    @Override public boolean isValidPriceRange(BigDecimal minPrice, BigDecimal maxPrice) { return true; }
    @Override public boolean isValidRatingRange(Double minRating, Double maxRating) { return true; }
    @Override public ProductSearchDto sanitizeSearchDto(ProductSearchDto searchDto) { return searchDto; }
    @Override public SearchResultDto addRecommendations(SearchResultDto results) { return results; }
    @Override public SearchResultDto addRelatedProducts(SearchResultDto results) { return results; }
    @Override public SearchResultDto addPopularProducts(SearchResultDto results) { return results; }
    @Override public void clearSearchCache() { }
    @Override public void clearSearchCache(String query) { }
    @Override public void warmupSearchCache() { }
    @Override public void updateSearchConfiguration(String key, Object value) { }
    @Override public Object getSearchConfiguration(String key) { return null; }
    @Override public void resetSearchConfiguration() { }
}
