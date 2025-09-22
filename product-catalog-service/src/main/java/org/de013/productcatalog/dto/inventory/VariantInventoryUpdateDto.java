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
@Schema(description = "Request to update variant inventory")
public class VariantInventoryUpdateDto {

    @Min(value = 0, message = "Minimum stock level must be non-negative")
    @Schema(description = "Minimum stock level before low stock alert", example = "5")
    private Integer minStockLevel;

    @Min(value = 0, message = "Maximum stock level must be non-negative")
    @Schema(description = "Maximum stock level for this variant", example = "200")
    private Integer maxStockLevel;

    @Min(value = 0, message = "Reorder point must be non-negative")
    @Schema(description = "Stock level at which reorder should be triggered", example = "10")
    private Integer reorderPoint;

    @Min(value = 0, message = "Reorder quantity must be non-negative")
    @Schema(description = "Quantity to reorder when reorder point is reached", example = "50")
    private Integer reorderQuantity;

    @Schema(description = "Whether to track inventory for this variant", example = "true")
    private Boolean trackInventory;

    @Schema(description = "Whether to allow backorders when out of stock", example = "false")
    private Boolean allowBackorder;

    @Schema(description = "Physical location of the variant in warehouse", example = "A1-B2-C3")
    private String location;

    @Schema(description = "Variant-specific SKU for inventory tracking", example = "PROD-001-L")
    private String sku;

    // Helper method to check if any field is set
    public boolean hasUpdates() {
        return minStockLevel != null || maxStockLevel != null || reorderPoint != null ||
               reorderQuantity != null || trackInventory != null || allowBackorder != null ||
               location != null || sku != null;
    }

    // Validation methods
    public boolean isMaxStockLevelValid() {
        if (maxStockLevel == null || minStockLevel == null) {
            return true; // Optional fields
        }
        return maxStockLevel >= minStockLevel;
    }

    public boolean isReorderPointValid() {
        if (reorderPoint == null) {
            return true;
        }
        if (maxStockLevel != null) {
            return reorderPoint <= maxStockLevel;
        }
        return true;
    }
}
