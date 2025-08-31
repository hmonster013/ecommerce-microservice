package org.de013.productcatalog.dto.product;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.de013.productcatalog.entity.enums.ProductStatus;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Product summary for listings")
public class ProductSummaryDto {

    @Schema(description = "Product ID", example = "1")
    private Long id;

    @Schema(description = "Product name", example = "iPhone 15 Pro")
    private String name;

    @Schema(description = "Short product description", example = "Premium smartphone with professional camera")
    private String shortDescription;

    @Schema(description = "Product SKU", example = "IPHONE-15-PRO-128")
    private String sku;

    @Schema(description = "Product price", example = "999.00")
    private BigDecimal price;

    @Schema(description = "Compare at price (original price)", example = "1099.00")
    private BigDecimal comparePrice;

    @Schema(description = "Product brand", example = "Apple")
    private String brand;

    @Schema(description = "Product status", example = "ACTIVE")
    private ProductStatus status;

    @Schema(description = "Is featured product", example = "true")
    private Boolean isFeatured;

    @Schema(description = "Main product image URL", example = "https://example.com/images/iphone-15-pro-main.jpg")
    private String mainImageUrl;

    @Schema(description = "Primary category name", example = "Smartphones")
    private String primaryCategoryName;

    @Schema(description = "Primary category slug", example = "smartphones")
    private String primaryCategorySlug;

    @Schema(description = "Average rating", example = "4.5")
    private Double averageRating;

    @Schema(description = "Total review count", example = "150")
    private Integer reviewCount;

    @Schema(description = "Available quantity", example = "50")
    private Integer availableQuantity;

    @Schema(description = "Is in stock", example = "true")
    private Boolean inStock;

    @Schema(description = "Pricing information")
    private PricingInfo pricing;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Pricing information")
    public static class PricingInfo {
        
        @Schema(description = "Current price", example = "999.00")
        private BigDecimal currentPrice;
        
        @Schema(description = "Original price", example = "1099.00")
        private BigDecimal originalPrice;
        
        @Schema(description = "Discount amount", example = "100.00")
        private BigDecimal discountAmount;
        
        @Schema(description = "Discount percentage", example = "9.09")
        private BigDecimal discountPercentage;
        
        @Schema(description = "Is on sale", example = "true")
        private Boolean onSale;
        
        @Schema(description = "Savings text", example = "Save $100.00 (9%)")
        private String savingsText;
    }

    // Helper methods
    @JsonIgnore
    public boolean isAvailable() {
        return status == ProductStatus.ACTIVE && (inStock == null || inStock);
    }

    @JsonIgnore
    public boolean isOnSale() {
        return pricing != null && pricing.getOnSale() != null && pricing.getOnSale();
    }

    @JsonIgnore
    public String getDisplayPrice() {
        if (price == null) {
            return "N/A";
        }
        return "$" + price.toString();
    }

    @JsonIgnore
    public String getDisplayComparePrice() {
        if (comparePrice == null) {
            return null;
        }
        return "$" + comparePrice.toString();
    }
}
