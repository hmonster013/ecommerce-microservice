package org.de013.productcatalog.dto.inventory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Inventory information")
public class InventoryResponseDto {

    @Schema(description = "Inventory ID", example = "1")
    private Long id;

    @Schema(description = "Product ID", example = "1")
    private Long productId;

    @Schema(description = "Total quantity", example = "50")
    private Integer quantity;

    @Schema(description = "Reserved quantity", example = "5")
    private Integer reservedQuantity;

    @Schema(description = "Available quantity", example = "45")
    private Integer availableQuantity;

    @Schema(description = "Minimum stock level", example = "10")
    private Integer minStockLevel;

    @Schema(description = "Maximum stock level", example = "100")
    private Integer maxStockLevel;

    @Schema(description = "Reorder point", example = "15")
    private Integer reorderPoint;

    @Schema(description = "Reorder quantity", example = "25")
    private Integer reorderQuantity;

    @Schema(description = "Track inventory", example = "true")
    private Boolean trackInventory;

    @Schema(description = "Allow backorder", example = "false")
    private Boolean allowBackorder;

    @Schema(description = "Storage location", example = "Warehouse A - Bin 123")
    private String location;

    @Schema(description = "Supplier SKU", example = "SUP-IPHONE-15-PRO")
    private String supplierSku;

    @Schema(description = "Is in stock", example = "true")
    private Boolean inStock;

    @Schema(description = "Is low stock", example = "false")
    private Boolean lowStock;

    @Schema(description = "Needs reorder", example = "false")
    private Boolean needsReorder;

    @Schema(description = "Stock status", example = "In Stock")
    private String stockStatus;

    // Helper methods
    @JsonIgnore
    public boolean canFulfillOrder(int requestedQuantity) {
        if (!trackInventory) {
            return true;
        }
        if (inStock && availableQuantity != null && availableQuantity >= requestedQuantity) {
            return true;
        }
        return allowBackorder != null && allowBackorder;
    }

    // Manual getters for critical methods (Lombok backup)
    @JsonIgnore
    public Integer getAvailableQuantity() {
        return availableQuantity;
    }
}
