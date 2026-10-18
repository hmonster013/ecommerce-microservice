package org.de013.productcatalog.integration;

import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.controller.SearchController;
import org.de013.productcatalog.dto.product.ProductSummaryDto;
import org.de013.productcatalog.dto.search.ProductSearchDto;
import org.de013.productcatalog.entity.enums.ProductStatus;
import org.de013.productcatalog.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SearchValidationAndContractTest {

    private SearchService searchService;
    private SearchController searchController;

    @BeforeEach
    void setUp() {
        searchService = mock(SearchService.class);
        searchController = new SearchController(searchService);
    }

    @Test
    void testSearchValidation_InvalidPageNegative() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                searchController.searchProducts(null, null, null, null, null, null, null, "name", "asc", -1, 20)
        );
        assertTrue(ex.getMessage().contains("Page index"));
    }

    @Test
    void testSearchValidation_InvalidSizeOutOfBounds() {
        assertThrows(IllegalArgumentException.class, () ->
                searchController.searchProducts(null, null, null, null, null, null, null, "name", "asc", 0, 0)
        );
        assertThrows(IllegalArgumentException.class, () ->
                searchController.searchProducts(null, null, null, null, null, null, null, "name", "asc", 0, 101)
        );
    }

    @Test
    void testSearchValidation_NegativePrices() {
        assertThrows(IllegalArgumentException.class, () ->
                searchController.searchProducts(null, null, null, new BigDecimal("-10"), null, null, null, "name", "asc", 0, 20)
        );
        assertThrows(IllegalArgumentException.class, () ->
                searchController.searchProducts(null, null, null, null, new BigDecimal("-5"), null, null, "name", "asc", 0, 20)
        );
    }

    @Test
    void testSearchValidation_MinPriceGreaterThanMaxPrice() {
        assertThrows(IllegalArgumentException.class, () ->
                searchController.searchProducts(null, null, null, new BigDecimal("100"), new BigDecimal("50"), null, null, "name", "asc", 0, 20)
        );
    }

    @Test
    void testSearchController_SetsPageSizeSortAndActiveStatusInDto() {
        PageResponse<ProductSummaryDto> mockResponse = PageResponse.<ProductSummaryDto>builder()
                .content(List.of())
                .page(2)
                .size(15)
                .totalElements(0)
                .totalPages(0)
                .first(false)
                .last(true)
                .empty(true)
                .build();

        when(searchService.advancedSearch(any(ProductSearchDto.class))).thenReturn(mockResponse);

        ResponseEntity<PageResponse<ProductSummaryDto>> response = searchController.searchProducts(
                "laptop", 5L, "Dell", new BigDecimal("500"), new BigDecimal("1500"),
                true, false, "price", "asc", 2, 15
        );

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        // Verify contract returns raw PageResponse, not an ApiResponse wrapper
        assertInstanceOf(PageResponse.class, response.getBody());

        ArgumentCaptor<ProductSearchDto> captor = ArgumentCaptor.forClass(ProductSearchDto.class);
        verify(searchService).advancedSearch(captor.capture());

        ProductSearchDto captured = captor.getValue();
        assertEquals("laptop", captured.getQuery());
        assertEquals(List.of(5L), captured.getCategoryIds());
        assertEquals(List.of("Dell"), captured.getBrands());
        assertEquals(new BigDecimal("500"), captured.getMinPrice());
        assertEquals(new BigDecimal("1500"), captured.getMaxPrice());
        assertEquals(true, captured.getInStockOnly());
        assertEquals(false, captured.getFeaturedOnly());
        assertEquals(2, captured.getPage());
        assertEquals(15, captured.getSize());
        assertEquals("price", captured.getSortBy());
        assertEquals("asc", captured.getSortDirection());
        assertEquals(List.of(ProductStatus.ACTIVE), captured.getStatuses(), "Public search must be scoped to ACTIVE only");
    }

    @Test
    void testSuggestions_ClampsLimitTo1Through20() {
        when(searchService.getSearchSuggestions(any(), anyInt())).thenReturn(List.of("result"));

        // Limit 100 should be clamped to 20
        searchController.getSearchSuggestions("query", 100);
        verify(searchService).getSearchSuggestions("query", 20);

        // Limit 0 should be clamped to 1
        searchController.getSearchSuggestions("query", 0);
        verify(searchService).getSearchSuggestions("query", 1);
    }
}
