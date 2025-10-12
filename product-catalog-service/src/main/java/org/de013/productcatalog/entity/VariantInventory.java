package org.de013.productcatalog.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "variant_inventory", indexes = {
        @Index(name = "idx_variant_inventory_variant_id", columnList = "variant_id"),
        @Index(name = "idx_variant_inventory_product_id", columnList = "product_id"),
        @Index(name = "idx_variant_inventory_low_stock", columnList = "quantity, min_stock_level"),
        @Index(name = "idx_variant_inventory_out_of_stock", columnList = "quantity"),
        @Index(name = "idx_variant_inventory_sku", columnList = "sku"),
        @Index(name = "idx_variant_inventory_location", columnList = "location"),
        @Index(name = "idx_variant_inventory_track_inventory", columnList = "track_inventory")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class VariantInventory extends BaseEntity {

    @NotNull(message = "Product variant is required")
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false, unique = true)
    private ProductVariant variant;

    @NotNull(message = "Product is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Min(value = 0, message = "Quantity must be non-negative")
    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private Integer quantity = 0;

    @Min(value = 0, message = "Reserved quantity must be non-negative")
    @Column(name = "reserved_quantity", nullable = false)
    @Builder.Default
    private Integer reservedQuantity = 0;

    @Min(value = 0, message = "Minimum stock level must be non-negative")
    @Column(name = "min_stock_level")
    @Builder.Default
    private Integer minStockLevel = 0;

    @Min(value = 0, message = "Maximum stock level must be non-negative")
    @Column(name = "max_stock_level")
    private Integer maxStockLevel;

    @Min(value = 0, message = "Reorder point must be non-negative")
    @Column(name = "reorder_point")
    @Builder.Default
    private Integer reorderPoint = 0;

    @Min(value = 0, message = "Reorder quantity must be non-negative")
    @Column(name = "reorder_quantity")
    private Integer reorderQuantity;

    @Column(name = "track_inventory", nullable = false)
    @Builder.Default
    private Boolean trackInventory = true;

    @Column(name = "allow_backorder", nullable = false)
    @Builder.Default
    private Boolean allowBackorder = false;

    @Column(name = "location")
    private String location; // Warehouse location or bin number

    @Column(name = "sku")
    private String sku; // Variant-specific SKU for inventory tracking
    
    // Helper methods
    public Integer getAvailableQuantity() {
        return Math.max(0, quantity - reservedQuantity);
    }

    public boolean isInStock() {
        return getAvailableQuantity() > 0;
    }

    public boolean isOutOfStock() {
        return getAvailableQuantity() <= 0;
    }

    public boolean isLowStock() {
        return getAvailableQuantity() <= minStockLevel;
    }

    public boolean needsReorder() {
        return getAvailableQuantity() <= reorderPoint;
    }

    public boolean canFulfillOrder(int requestedQuantity) {
        if (!trackInventory) {
            return true; // If not tracking inventory, assume always available
        }
        
        if (isInStock() && getAvailableQuantity() >= requestedQuantity) {
            return true;
        }
        
        return allowBackorder;
    }

    public void reserveStock(int quantityToReserve) {
        if (canFulfillOrder(quantityToReserve)) {
            this.reservedQuantity += quantityToReserve;
        } else {
            throw new IllegalStateException("Cannot reserve " + quantityToReserve + " items for variant " + 
                    (variant != null ? variant.getValue() : "unknown") + ". Available: " + getAvailableQuantity());
        }
    }

    public void releaseReservedStock(int quantityToRelease) {
        this.reservedQuantity = Math.max(0, this.reservedQuantity - quantityToRelease);
    }

    public void fulfillOrder(int quantityToFulfill) {
        if (this.reservedQuantity >= quantityToFulfill) {
            this.reservedQuantity -= quantityToFulfill;
            this.quantity = Math.max(0, this.quantity - quantityToFulfill);
        } else {
            throw new IllegalStateException("Cannot fulfill " + quantityToFulfill + " items for variant " + 
                    (variant != null ? variant.getValue() : "unknown") + ". Reserved: " + this.reservedQuantity);
        }
    }

    public void addStock(int quantityToAdd) {
        this.quantity += quantityToAdd;
    }

    public void removeStock(int quantityToRemove) {
        if (quantityToRemove > this.quantity) {
            throw new IllegalArgumentException(
                String.format("Cannot remove %d items from variant %s. Only %d available in stock",
                    quantityToRemove, (variant != null ? variant.getValue() : "unknown"), this.quantity)
            );
        }
        this.quantity -= quantityToRemove;
    }

    public String getStockStatus() {
        if (isOutOfStock()) {
            return "Out of Stock";
        } else if (isLowStock()) {
            return "Low Stock";
        } else if (needsReorder()) {
            return "Needs Reorder";
        } else {
            return "In Stock";
        }
    }

    public String getVariantDisplayName() {
        if (variant != null) {
            return variant.getFullName();
        }
        return "Unknown Variant";
    }

    // Overloaded methods for Integer parameters
    public void addStock(Integer quantityToAdd) {
        if (quantityToAdd != null && quantityToAdd > 0) {
            addStock(quantityToAdd.intValue());
        }
    }

    public void removeStock(Integer quantityToRemove) {
        if (quantityToRemove != null && quantityToRemove > 0) {
            removeStock(quantityToRemove.intValue());
        }
    }

    public boolean canFulfillOrder(Integer requestedQuantity) {
        if (requestedQuantity == null) return false;
        return canFulfillOrder(requestedQuantity.intValue());
    }

    @Override
    public String toString() {
        return "VariantInventory{" +
                "id=" + super.getId() +
                ", variant=" + getVariantDisplayName() +
                ", quantity=" + quantity +
                ", reservedQuantity=" + reservedQuantity +
                ", availableQuantity=" + getAvailableQuantity() +
                ", stockStatus='" + getStockStatus() + '\'' +
                '}';
    }
}
