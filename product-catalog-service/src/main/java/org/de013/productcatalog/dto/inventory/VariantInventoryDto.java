package org.de013.productcatalog.dto.inventory;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
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
    public Integer getAvailableQuantity() {
        if (quantity == null || reservedQuantity == null) {
            return 0;
        }
        return Math.max(0, quantity - reservedQuantity);
    }

    public Boolean getInStock() {
        return getAvailableQuantity() > 0;
    }

    public Boolean getOutOfStock() {
        return getAvailableQuantity() <= 0;
    }

    public Boolean getLowStock() {
        if (minStockLevel == null) {
            return false;
        }
        return getAvailableQuantity() <= minStockLevel;
    }

    public Boolean getNeedsReorder() {
        if (reorderPoint == null) {
            return false;
        }
        return getAvailableQuantity() <= reorderPoint;
    }

    public Boolean getCanFulfillOrders() {
        if (trackInventory == null || !trackInventory) {
            return true; // If not tracking inventory, assume always available
        }
        
        if (getInStock()) {
            return true;
        }
        
        return allowBackorder != null && allowBackorder;
    }

    public String getStockStatus() {
        if (getOutOfStock()) {
            return "Out of Stock";
        } else if (getLowStock()) {
            return "Low Stock";
        } else if (getNeedsReorder()) {
            return "Needs Reorder";
        } else {
            return "In Stock";
        }
    }
}
