package org.de013.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * Shared Inventory DTO for communication between services
 * Used by Product Catalog Service and Shopping Cart Service
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Inventory information")
public class InventoryDto {

    @Schema(description = "Inventory ID", example = "1")
    private Long id;

    @Schema(description = "Product ID", example = "1")
    private Long productId;

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

    @Schema(description = "Whether inventory is tracked", example = "true")
    private Boolean trackInventory;

    @Schema(description = "Whether backorders are allowed", example = "false")
    private Boolean allowBackorder;

    @Schema(description = "Storage location", example = "Warehouse A - Bin 123")
    private String location;

    @Schema(description = "Supplier SKU", example = "SUP-IPHONE-15-PRO")
    private String supplierSku;

    @Schema(description = "Stock status", example = "In Stock", allowableValues = {"In Stock", "Low Stock", "Out of Stock", "Needs Reorder"})
    private String stockStatus;

    @Schema(description = "Whether item is in stock", example = "true")
    private Boolean inStock;

    @Schema(description = "Whether item is out of stock", example = "false")
    private Boolean outOfStock;

    @Schema(description = "Whether item has low stock", example = "false")
    private Boolean lowStock;

    @Schema(description = "Whether item needs reorder", example = "false")
    private Boolean needsReorder;

    @Schema(description = "Whether item can fulfill orders", example = "true")
    private Boolean canFulfillOrders;

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
