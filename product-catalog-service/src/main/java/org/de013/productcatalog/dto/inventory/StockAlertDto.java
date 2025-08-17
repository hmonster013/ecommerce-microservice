package org.de013.productcatalog.dto.inventory;

import com.fasterxml.jackson.annotation.JsonFormat;
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
@Schema(description = "Stock alert information")
public class StockAlertDto {

    @Schema(description = "Product ID", example = "1")
    private Long productId;

    @Schema(description = "Product name", example = "iPhone 15 Pro")
    private String productName;

    @Schema(description = "Product SKU", example = "IPHONE-15-PRO-128")
    private String productSku;

    @Schema(description = "Current quantity", example = "5")
    private Integer currentQuantity;

    @Schema(description = "Reserved quantity", example = "2")
    private Integer reservedQuantity;

    @Schema(description = "Available quantity", example = "3")
    private Integer availableQuantity;

    @Schema(description = "Minimum stock level", example = "10")
    private Integer minStockLevel;

    @Schema(description = "Reorder point", example = "15")
    private Integer reorderPoint;

    @Schema(description = "Reorder quantity", example = "25")
    private Integer reorderQuantity;

    @Schema(description = "Alert type", example = "LOW_STOCK")
    private AlertType alertType;

    @Schema(description = "Alert severity", example = "WARNING")
    private AlertSeverity severity;

    @Schema(description = "Alert message", example = "Product is running low on stock")
    private String message;

    @Schema(description = "Storage location", example = "Warehouse A - Bin 123")
    private String location;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "Alert timestamp", example = "2024-01-15 10:30:00")
    @Builder.Default
    private LocalDateTime alertTime = LocalDateTime.now();

    @Schema(description = "Recommended action", example = "Reorder 25 units")
    private String recommendedAction;

    public enum AlertType {
        OUT_OF_STOCK,
        LOW_STOCK,
        NEEDS_REORDER,
        OVERSTOCK
    }

    public enum AlertSeverity {
        INFO,
        WARNING,
        CRITICAL
    }

    // Helper methods
    public String getDisplayMessage() {
        return switch (alertType) {
            case OUT_OF_STOCK -> String.format("Product '%s' is out of stock", productName);
            case LOW_STOCK -> String.format("Product '%s' is low on stock (%d remaining)", productName, availableQuantity);
            case NEEDS_REORDER -> String.format("Product '%s' needs reordering (%d remaining)", productName, availableQuantity);
            case OVERSTOCK -> String.format("Product '%s' is overstocked (%d available)", productName, availableQuantity);
        };
    }

    public String getRecommendedAction() {
        if (recommendedAction != null) {
            return recommendedAction;
        }
        
        return switch (alertType) {
            case OUT_OF_STOCK -> "Immediate reorder required";
            case LOW_STOCK, NEEDS_REORDER -> reorderQuantity != null ? 
                String.format("Reorder %d units", reorderQuantity) : "Reorder required";
            case OVERSTOCK -> "Consider reducing order quantities";
        };
    }
}
