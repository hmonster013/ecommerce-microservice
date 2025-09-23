package org.de013.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
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
    private String status;

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

    @Schema(description = "Inventory information")
    private InventoryDto inventory;

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

    // Computed fields for backward compatibility
    @Schema(description = "Whether the product is available for purchase", example = "true")
    private Boolean available;

    // Nested DTOs
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "Category summary")
    public static class CategorySummaryDto {
        private Long id;
        private String name;
        private String slug;
        private String description;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "Product image")
    public static class ProductImageDto {
        private Long id;
        private String imageUrl;
        private String altText;
        private String imageType;
        private Integer sortOrder;
    }



    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "Pricing information")
    public static class PricingInfo {
        private BigDecimal currentPrice;
        private BigDecimal originalPrice;
        private BigDecimal discountAmount;
        private BigDecimal discountPercentage;
        private Boolean onSale;
        private String savingsText;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "Shipping information")
    public static class ShippingInfo {
        private Boolean requiresShipping;
        private BigDecimal weight;
        private String dimensions;
        private String estimatedDelivery;
        private Boolean freeShippingEligible;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "Product specification")
    public static class ProductSpecification {
        private String name;
        private String value;
        private String group;
    }

    // Helper methods
    @JsonIgnore
    public boolean isAvailable() {
        return "ACTIVE".equals(status) &&
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

    // Backward compatibility methods for Shopping Cart Service
    @JsonIgnore
    public String getImageUrl() {
        ProductImageDto mainImage = getMainImage();
        return mainImage != null ? mainImage.getImageUrl() : null;
    }

    @JsonIgnore
    public String getCategoryId() {
        return primaryCategory != null ? primaryCategory.getId().toString() : null;
    }

    @JsonIgnore
    public String getCategoryName() {
        return primaryCategory != null ? primaryCategory.getName() : null;
    }

    @JsonIgnore
    public BigDecimal getCurrentPrice() {
        return pricing != null ? pricing.getCurrentPrice() : price;
    }

    @JsonIgnore
    public BigDecimal getOriginalPrice() {
        return pricing != null ? pricing.getOriginalPrice() : comparePrice;
    }

    @JsonIgnore
    public Integer getStockQuantity() {
        if (inventory != null && inventory.getAvailableQuantity() != null) {
            return inventory.getAvailableQuantity();
        }
        return 0; // Default to 0 if inventory or availableQuantity is null
    }
}
