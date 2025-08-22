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

    @Min(value = 0, message = "{inventory.quantity.negative}")
    @Schema(description = "Total quantity", example = "50")
    private Integer quantity;

    @Min(value = 0, message = "{field.non.negative.required}")
    @Schema(description = "Reserved quantity", example = "5")
    private Integer reservedQuantity;

    @Min(value = 0, message = "{field.non.negative.required}")
    @Schema(description = "Minimum stock level", example = "10")
    private Integer minStockLevel;

    @Min(value = 0, message = "{inventory.max.stock.invalid}")
    @Schema(description = "Maximum stock level", example = "100")
    private Integer maxStockLevel;

    @Min(value = 0, message = "{inventory.reorder.level.invalid}")
    @Schema(description = "Reorder point", example = "15")
    private Integer reorderPoint;

    @Min(value = 0, message = "{field.non.negative.required}")
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
