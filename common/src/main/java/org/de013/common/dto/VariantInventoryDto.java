package org.de013.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Shared Variant Inventory DTO for communication between services
 * Used by Product Catalog Service and Shopping Cart Service
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Variant inventory information")
public class VariantInventoryDto {

    @Schema(description = "Inventory ID", example = "1")
    private Long id;

    @Schema(description = "Variant ID", example = "1")
    private Long variantId;

    @Schema(description = "Product ID", example = "1")
    private Long productId;

    @Schema(description = "Variant name", example = "Size: Large")
    private String variantName;

    @Schema(description = "Variant value", example = "Large")
    private String variantValue;

    @Schema(description = "Variant type", example = "SIZE")
    private String variantType;

    @Schema(description = "Total quantity in stock", example = "100")
    private Integer quantity;

    @Schema(description = "Reserved quantity for pending orders", example = "10")
    private Integer reservedQuantity;

    @Schema(description = "Available quantity (quantity - reserved)", example = "90")
    private Integer availableQuantity;

    @Schema(description = "Minimum stock level", example = "5")
    private Integer minStockLevel;

    @Schema(description = "Maximum stock level", example = "200")
    private Integer maxStockLevel;

    @Schema(description = "Reorder point", example = "10")
    private Integer reorderPoint;

    @Schema(description = "Reorder quantity", example = "50")
    private Integer reorderQuantity;

    @Schema(description = "Whether inventory is tracked for this variant", example = "true")
    private Boolean trackInventory;

    @Schema(description = "Whether backorders are allowed", example = "false")
    private Boolean allowBackorder;

    @Schema(description = "Storage location", example = "A1-B2-C3")
    private String location;

    @Schema(description = "Variant SKU for inventory tracking", example = "PROD-001-L")
    private String sku;

    @Schema(description = "Stock status", example = "In Stock", allowableValues = {"In Stock", "Low Stock", "Out of Stock", "Needs Reorder"})
    private String stockStatus;

    @Schema(description = "Whether variant is in stock", example = "true")
    private Boolean inStock;

    @Schema(description = "Whether variant is out of stock", example = "false")
    private Boolean outOfStock;

    @Schema(description = "Whether variant has low stock", example = "false")
    private Boolean lowStock;

    @Schema(description = "Whether variant needs reorder", example = "false")
    private Boolean needsReorder;

    @Schema(description = "Whether variant can fulfill orders", example = "true")
    private Boolean canFulfillOrders;

    @Schema(description = "Creation timestamp", example = "2024-01-15T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp", example = "2024-01-15T14:45:00")
    private LocalDateTime updatedAt;

    @Schema(description = "Created by user", example = "admin")
    private String createdBy;

    @Schema(description = "Updated by user", example = "admin")
    private String updatedBy;

    // Helper methods for computed properties
    @JsonIgnore
    public Integer getCalculatedAvailableQuantity() {
        if (quantity == null || reservedQuantity == null) {
            return quantity != null ? quantity : 0;
        }
        return Math.max(0, quantity - reservedQuantity);
    }

    @JsonIgnore
    public Boolean isInStock() {
        if (inStock != null) {
            return inStock;
        }
        return getCalculatedAvailableQuantity() > 0;
    }

    @JsonIgnore
    public Boolean isOutOfStock() {
        if (outOfStock != null) {
            return outOfStock;
        }
        return getCalculatedAvailableQuantity() <= 0;
    }

    @JsonIgnore
    public Boolean isLowStock() {
        if (lowStock != null) {
            return lowStock;
        }
        if (minStockLevel == null) {
            return false;
        }
        return getCalculatedAvailableQuantity() <= minStockLevel;
    }

    @JsonIgnore
    public Boolean needsReorder() {
        if (needsReorder != null) {
            return needsReorder;
        }
        if (reorderPoint == null) {
            return false;
        }
        return getCalculatedAvailableQuantity() <= reorderPoint;
    }

    @JsonIgnore
    public boolean canFulfillOrder(int requestedQuantity) {
        if (trackInventory != null && !trackInventory) {
            return true;
        }
        if (isInStock() && getCalculatedAvailableQuantity() >= requestedQuantity) {
            return true;
        }
        return allowBackorder != null && allowBackorder;
    }

    // Ensure availableQuantity is always calculated if not set
    public Integer getAvailableQuantity() {
        if (availableQuantity != null) {
            return availableQuantity;
        }
        return getCalculatedAvailableQuantity();
    }
}
