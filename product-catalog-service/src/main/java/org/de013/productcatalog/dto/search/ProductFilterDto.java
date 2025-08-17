package org.de013.productcatalog.dto.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Product filter options")
public class ProductFilterDto {

    @Schema(description = "Available categories")
    private List<CategoryFilter> categories;

    @Schema(description = "Available brands")
    private List<BrandFilter> brands;

    @Schema(description = "Price range")
    private PriceRange priceRange;

    @Schema(description = "Rating options")
    private List<RatingFilter> ratings;

    @Schema(description = "Available features")
    private List<FeatureFilter> features;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Category filter")
    public static class CategoryFilter {
        
        @Schema(description = "Category ID", example = "1")
        private Long id;
        
        @Schema(description = "Category name", example = "Electronics")
        private String name;
        
        @Schema(description = "Category slug", example = "electronics")
        private String slug;
        
        @Schema(description = "Product count", example = "150")
        private Integer productCount;
        
        @Schema(description = "Child categories")
        private List<CategoryFilter> children;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Brand filter")
    public static class BrandFilter {
        
        @Schema(description = "Brand name", example = "Apple")
        private String name;
        
        @Schema(description = "Product count", example = "25")
        private Integer productCount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Price range")
    public static class PriceRange {
        
        @Schema(description = "Minimum price", example = "0.00")
        private BigDecimal min;
        
        @Schema(description = "Maximum price", example = "2999.99")
        private BigDecimal max;
        
        @Schema(description = "Price ranges")
        private List<PriceRangeOption> ranges;
        
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @Schema(description = "Price range option")
        public static class PriceRangeOption {
            
            @Schema(description = "Range label", example = "$100 - $500")
            private String label;
            
            @Schema(description = "Minimum price", example = "100.00")
            private BigDecimal min;
            
            @Schema(description = "Maximum price", example = "500.00")
            private BigDecimal max;
            
            @Schema(description = "Product count", example = "45")
            private Integer productCount;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Rating filter")
    public static class RatingFilter {
        
        @Schema(description = "Minimum rating", example = "4")
        private Integer minRating;
        
        @Schema(description = "Rating label", example = "4 stars & up")
        private String label;
        
        @Schema(description = "Product count", example = "120")
        private Integer productCount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Feature filter")
    public static class FeatureFilter {
        
        @Schema(description = "Feature name", example = "Featured")
        private String name;
        
        @Schema(description = "Feature key", example = "featured")
        private String key;
        
        @Schema(description = "Product count", example = "15")
        private Integer productCount;
    }
}
