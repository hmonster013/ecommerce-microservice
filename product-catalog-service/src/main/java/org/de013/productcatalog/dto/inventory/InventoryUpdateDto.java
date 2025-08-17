package org.de013.productcatalog.dto.inventory;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Inventory update request")
public class InventoryUpdateDto {

    @Min(value = 0, message = "Quantity must be non-negative")
    @Schema(description = "Total quantity", example = "50")
    private Integer quantity;

    @Min(value = 0, message = "Reserved quantity must be non-negative")
    @Schema(description = "Reserved quantity", example = "5")
    private Integer reservedQuantity;

    @Min(value = 0, message = "Minimum stock level must be non-negative")
    @Schema(description = "Minimum stock level", example = "10")
    private Integer minStockLevel;

    @Min(value = 0, message = "Maximum stock level must be non-negative")
    @Schema(description = "Maximum stock level", example = "100")
    private Integer maxStockLevel;

    @Min(value = 0, message = "Reorder point must be non-negative")
    @Schema(description = "Reorder point", example = "15")
    private Integer reorderPoint;

    @Min(value = 0, message = "Reorder quantity must be non-negative")
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

    // Helper method to check if any field is set
    public boolean hasUpdates() {
        return quantity != null || reservedQuantity != null || minStockLevel != null ||
               maxStockLevel != null || reorderPoint != null || reorderQuantity != null ||
               trackInventory != null || allowBackorder != null || location != null ||
               supplierSku != null;
    }
}
