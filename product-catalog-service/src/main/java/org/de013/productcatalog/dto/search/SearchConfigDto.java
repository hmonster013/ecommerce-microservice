package org.de013.productcatalog.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * DTO for search configuration settings.
 */
@Data
@Builder
@Schema(description = "Search configuration settings")
public class SearchConfigDto {

    @Schema(description = "Maximum number of search results per page", example = "50")
    private Integer maxPageSize;

    @Schema(description = "Default page size for search results", example = "20")
    private Integer defaultPageSize;

    @Schema(description = "Maximum search query length", example = "200")
    private Integer maxQueryLength;

    @Schema(description = "Search timeout in milliseconds", example = "5000")
    private Long searchTimeoutMs;

    @Schema(description = "Enable search analytics tracking", example = "true")
    private Boolean enableAnalytics;

    @Schema(description = "Enable search result caching", example = "true")
    private Boolean enableCaching;

    @Schema(description = "Cache TTL in seconds", example = "300")
    private Long cacheTtlSeconds;

    @Schema(description = "Enable autocomplete suggestions", example = "true")
    private Boolean enableAutocomplete;

    @Schema(description = "Minimum query length for autocomplete", example = "2")
    private Integer autocompleteMinLength;

    @Schema(description = "Maximum autocomplete suggestions", example = "10")
    private Integer autocompleteMaxSuggestions;

    @Schema(description = "Enable search suggestions for no results", example = "true")
    private Boolean enableDidYouMean;

    @Schema(description = "Enable related searches", example = "true")
    private Boolean enableRelatedSearches;

    @Schema(description = "Default sort criteria", example = "relevance")
    private String defaultSort;

    @Schema(description = "Available sort options")
    private List<String> availableSortOptions;

    @Schema(description = "Search boost factors for different fields")
    private Map<String, Double> fieldBoosts;

    @Schema(description = "Minimum score threshold for search results", example = "0.1")
    private Double minScoreThreshold;

    @Schema(description = "Enable fuzzy matching", example = "true")
    private Boolean enableFuzzyMatching;

    @Schema(description = "Fuzzy matching edit distance", example = "2")
    private Integer fuzzyEditDistance;

    @Schema(description = "Enable search result highlighting", example = "true")
    private Boolean enableHighlighting;

    @Schema(description = "Maximum highlighted fragments", example = "3")
    private Integer maxHighlightFragments;

    @Schema(description = "Enable search facets", example = "true")
    private Boolean enableFacets;

    @Schema(description = "Maximum facet values per field", example = "20")
    private Integer maxFacetValues;

    @Schema(description = "Search index refresh interval in seconds", example = "60")
    private Long indexRefreshIntervalSeconds;

    @Schema(description = "Enable search performance monitoring", example = "true")
    private Boolean enablePerformanceMonitoring;

    @Schema(description = "Slow query threshold in milliseconds", example = "1000")
    private Long slowQueryThresholdMs;
}
