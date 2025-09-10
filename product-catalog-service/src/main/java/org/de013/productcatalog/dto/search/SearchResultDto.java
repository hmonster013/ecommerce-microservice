package org.de013.productcatalog.dto.search;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.de013.productcatalog.dto.product.ProductSummaryDto;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Search result")
public class SearchResultDto {

    @Schema(description = "Search query", example = "iphone")
    private String query;

    @Schema(description = "Total results found", example = "150")
    private Long totalResults;

    @Schema(description = "Total pages available", example = "8")
    private Integer totalPages;

    @Schema(description = "Current page number", example = "0")
    private Integer currentPage;

    @Schema(description = "Page size", example = "20")
    private Integer pageSize;

    @Schema(description = "Has next page", example = "true")
    private Boolean hasNext;

    @Schema(description = "Has previous page", example = "false")
    private Boolean hasPrevious;

    @Schema(description = "Search execution time in milliseconds", example = "45")
    private Long executionTimeMs;

    @Schema(description = "Products found")
    private List<ProductSummaryDto> products;

    @Schema(description = "Applied filters")
    private SearchFilters appliedFilters;

    @Schema(description = "Available filters")
    private ProductFilterDto availableFilters;

    @Schema(description = "Search suggestions")
    private List<String> suggestions;

    @Schema(description = "Did you mean suggestions")
    private List<String> didYouMean;

    @Schema(description = "Related searches")
    private List<String> relatedSearches;

    @Schema(description = "Search metadata")
    private SearchMetadata metadata;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Applied search filters")
    public static class SearchFilters {
        
        @Schema(description = "Category filters")
        private List<String> categories;
        
        @Schema(description = "Brand filters")
        private List<String> brands;
        
        @Schema(description = "Price range")
        private PriceRangeDto priceRange;
        
        @Schema(description = "Minimum rating")
        private Double minRating;
        
        @Schema(description = "Feature filters")
        private List<String> features;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Search metadata")
    public static class SearchMetadata {
        
        @Schema(description = "Search type", example = "full_text")
        private String searchType;
        
        @Schema(description = "Sort field", example = "relevance")
        private String sortBy;
        
        @Schema(description = "Sort direction", example = "DESC")
        private String sortDirection;
        
        @Schema(description = "Page number", example = "0")
        private Integer page;
        
        @Schema(description = "Page size", example = "20")
        private Integer size;
        
        @Schema(description = "Has more results", example = "true")
        private Boolean hasMore;
        
        @Schema(description = "Search ID for analytics", example = "search_123456")
        private String searchId;
    }

    // Helper methods
    @JsonIgnore
    public boolean hasResults() {
        return products != null && !products.isEmpty();
    }

    @JsonIgnore
    public boolean hasQuery() {
        return query != null && !query.trim().isEmpty();
    }

    @JsonIgnore
    public boolean hasSuggestions() {
        return suggestions != null && !suggestions.isEmpty();
    }

    @JsonIgnore
    public boolean hasDidYouMean() {
        return didYouMean != null && !didYouMean.isEmpty();
    }

    @JsonIgnore
    public String getResultSummary() {
        if (totalResults == null) {
            return "No results";
        }
        
        if (totalResults == 0) {
            return "No results found";
        } else if (totalResults == 1) {
            return "1 result found";
        } else {
            return String.format("%,d results found", totalResults);
        }
    }

    @JsonIgnore
    public String getExecutionTimeSummary() {
        if (executionTimeMs == null) {
            return "";
        }
        
        if (executionTimeMs < 1000) {
            return String.format("(%d ms)", executionTimeMs);
        } else {
            return String.format("(%.1f s)", executionTimeMs / 1000.0);
        }
    }

    // Manual getters for critical methods (Lombok backup)
    public String getQuery() {
        return query;
    }

    public Long getTotalResults() {
        return totalResults;
    }

    public List<ProductSummaryDto> getProducts() {
        return products;
    }

    public void setDidYouMean(List<String> didYouMean) {
        this.didYouMean = didYouMean;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    public void setRelatedSearches(List<String> relatedSearches) {
        this.relatedSearches = relatedSearches;
    }
}
