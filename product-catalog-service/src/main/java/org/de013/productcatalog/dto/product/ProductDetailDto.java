package org.de013.productcatalog.dto.product;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.de013.productcatalog.dto.category.CategorySummaryDto;
import org.de013.common.dto.InventoryDto;

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
@Schema(description = "Detailed product information")
public class ProductDetailDto {

    @Schema(description = "Product ID", example = "1")
    private Long id;

    @Schema(description = "Product name", example = "iPhone 15 Pro")
    private String name;

    @Schema(description = "Product description", example = "Latest iPhone with advanced camera system")
    private String description;

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

    @Schema(description = "Product weight in kg", example = "0.187")
    private BigDecimal weight;

    @Schema(description = "Product dimensions", example = "14.7 x 7.1 x 0.8 cm")
    private String dimensions;

    @Schema(description = "Product status", example = "ACTIVE")
    private ProductStatus status;

    @Schema(description = "Is featured product", example = "true")
    private Boolean isFeatured;

    @Schema(description = "Is digital product", example = "false")
    private Boolean isDigital;

    @Schema(description = "Requires shipping", example = "true")
    private Boolean requiresShipping;

    @Schema(description = "SEO meta title", example = "iPhone 15 Pro - Premium Smartphone")
    private String metaTitle;

    @Schema(description = "SEO meta description", example = "Latest iPhone with advanced camera system and A17 Pro chip")
    private String metaDescription;

    @Schema(description = "Search keywords", example = "iphone, apple, smartphone, mobile, camera, a17")
    private String searchKeywords;

    @Schema(description = "Product categories")
    private List<CategorySummaryDto> categories;

    @Schema(description = "Primary category")
    private CategorySummaryDto primaryCategory;

    @Schema(description = "Product images")
    private List<ProductImageDto> images;

    @Schema(description = "Product variants grouped by type")
    private List<ProductVariantGroupDto> variantGroups;

    @Schema(description = "Inventory information")
    private InventoryDto inventory;



    @Schema(description = "Related products")
    private List<ProductSummaryDto> relatedProducts;

    @Schema(description = "Pricing information")
    private PricingInfo pricing;

    @Schema(description = "Shipping information")
    private ShippingInfo shipping;

    @Schema(description = "Product specifications")
    private List<ProductSpecification> specifications;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "Creation timestamp", example = "2024-01-15 10:30:00")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "Last update timestamp", example = "2024-01-15 10:30:00")
    private LocalDateTime updatedAt;

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

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Shipping information")
    public static class ShippingInfo {
        
        @Schema(description = "Requires shipping", example = "true")
        private Boolean requiresShipping;
        
        @Schema(description = "Weight in kg", example = "0.187")
        private BigDecimal weight;
        
        @Schema(description = "Dimensions", example = "14.7 x 7.1 x 0.8 cm")
        private String dimensions;
        
        @Schema(description = "Estimated delivery time", example = "2-3 business days")
        private String estimatedDelivery;
        
        @Schema(description = "Free shipping eligible", example = "true")
        private Boolean freeShippingEligible;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Product specification")
    public static class ProductSpecification {
        
        @Schema(description = "Specification name", example = "Display")
        private String name;
        
        @Schema(description = "Specification value", example = "6.1-inch Super Retina XDR")
        private String value;
        
        @Schema(description = "Specification group", example = "Display & Design")
        private String group;
    }

    // Computed fields for backward compatibility
    @Schema(description = "Whether the product is available for purchase", example = "true")
    private Boolean available;

    // Helper methods
    @JsonIgnore
    public boolean isAvailable() {
        return status == ProductStatus.ACTIVE &&
               inventory != null &&
               inventory.getAvailableQuantity() != null &&
               inventory.getAvailableQuantity() > 0;
    }

    @JsonIgnore
    public boolean isOnSale() {
        return pricing != null && pricing.getOnSale() != null && pricing.getOnSale();
    }

    @JsonIgnore
    public ProductImageDto getMainImage() {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream()
                .filter(img -> "MAIN".equals(img.getImageType()))
                .findFirst()
                .orElse(images.get(0));
    }
}
