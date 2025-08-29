package org.de013.productcatalog.dto.search;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import org.de013.productcatalog.entity.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Product search request")
public class ProductSearchDto {

    @Schema(description = "Search query", example = "iphone")
    private String query;

    @Schema(description = "Category IDs to filter by", example = "[1, 6]")
    private List<Long> categoryIds;

    @Schema(description = "Brand names to filter by", example = "[\"Apple\", \"Samsung\"]")
    private List<String> brands;

    @Schema(description = "Product statuses to filter by", example = "[\"ACTIVE\"]")
    private List<ProductStatus> statuses;

    @Schema(description = "Minimum price", example = "100.00")
    private BigDecimal minPrice;

    @Schema(description = "Maximum price", example = "2000.00")
    private BigDecimal maxPrice;

    @Schema(description = "Minimum rating", example = "4.0")
    @Min(value = 1, message = "{review.rating.invalid}")
    @Max(value = 5, message = "{review.rating.invalid}")
    private Double minRating;

    @Schema(description = "Only featured products", example = "false")
    private Boolean featuredOnly;

    @Schema(description = "Only products in stock", example = "true")
    private Boolean inStockOnly;

    @Schema(description = "Filter by stock availability", example = "true")
    private Boolean inStock;

    @Schema(description = "Only featured products", example = "false")
    private Boolean featured;

    @Schema(description = "Only products on sale", example = "false")
    private Boolean onSale;

    @Schema(description = "Created after this date")
    private LocalDateTime createdAfter;

    @Schema(description = "Created before this date")
    private LocalDateTime createdBefore;

    @Schema(description = "Sort criteria", example = "price_asc")
    private String sort;

    @Schema(description = "Only products on sale", example = "false")
    private Boolean onSaleOnly;

    @Schema(description = "Only digital products", example = "false")
    private Boolean digitalOnly;

    @Schema(description = "Search in product names", example = "true")
    @Builder.Default
    private Boolean searchInName = true;

    @Schema(description = "Search in product descriptions", example = "true")
    @Builder.Default
    private Boolean searchInDescription = true;

    @Schema(description = "Search in product SKUs", example = "false")
    @Builder.Default
    private Boolean searchInSku = false;

    @Schema(description = "Search in brands", example = "true")
    @Builder.Default
    private Boolean searchInBrand = true;

    @Schema(description = "Search in keywords", example = "true")
    @Builder.Default
    private Boolean searchInKeywords = true;

    @Schema(description = "Sort field", example = "name")
    private String sortBy;

    @Schema(description = "Sort direction", example = "ASC")
    private String sortDirection;

    @Schema(description = "Page number (0-based)", example = "0")
    @Min(value = 0, message = "{search.page.invalid}")
    @Builder.Default
    private Integer page = 0;

    @Schema(description = "Page size", example = "20")
    @Min(value = 1, message = "{search.size.invalid}")
    @Max(value = 100, message = "{search.size.invalid}")
    @Builder.Default
    private Integer size = 20;

    // Helper methods
    @JsonIgnore
    public boolean hasQuery() {
        return query != null && !query.trim().isEmpty();
    }

    @JsonIgnore
    public boolean hasPriceRange() {
        return minPrice != null || maxPrice != null;
    }

    @JsonIgnore
    public boolean hasFilters() {
        return (categoryIds != null && !categoryIds.isEmpty()) ||
               (brands != null && !brands.isEmpty()) ||
               (statuses != null && !statuses.isEmpty()) ||
               hasPriceRange() ||
               minRating != null ||
               Boolean.TRUE.equals(featuredOnly) ||
               Boolean.TRUE.equals(inStockOnly) ||
               Boolean.TRUE.equals(onSaleOnly) ||
               Boolean.TRUE.equals(digitalOnly);
    }

    public String getEffectiveSortBy() {
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            return sortBy.trim();
        }
        return hasQuery() ? "relevance" : "name";
    }

    public String getEffectiveSortDirection() {
        if (sortDirection != null && !sortDirection.trim().isEmpty()) {
            return sortDirection.trim().toUpperCase();
        }
        return "ASC";
    }

    // Manual getters for critical methods (Lombok backup)
    public String getQuery() {
        return query;
    }

    public Integer getPage() {
        return page;
    }

    public Integer getSize() {
        return size;
    }
}
