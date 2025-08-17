package org.de013.productcatalog.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Table(name = "inventory", indexes = {
        @Index(name = "idx_inventory_product_id", columnList = "product_id"),
        @Index(name = "idx_inventory_low_stock", columnList = "quantity, min_stock_level"),
        @Index(name = "idx_inventory_out_of_stock", columnList = "quantity")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Product is required")
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
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

    @Column(name = "supplier_sku")
    private String supplierSku;

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
            throw new IllegalStateException("Cannot reserve " + quantityToReserve + " items. Available: " + getAvailableQuantity());
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
            throw new IllegalStateException("Cannot fulfill " + quantityToFulfill + " items. Reserved: " + this.reservedQuantity);
        }
    }

    public void addStock(int quantityToAdd) {
        this.quantity += quantityToAdd;
    }

    public void removeStock(int quantityToRemove) {
        this.quantity = Math.max(0, this.quantity - quantityToRemove);
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

    @Override
    public String toString() {
        return "Inventory{" +
                "id=" + id +
                ", quantity=" + quantity +
                ", reservedQuantity=" + reservedQuantity +
                ", availableQuantity=" + getAvailableQuantity() +
                ", minStockLevel=" + minStockLevel +
                ", trackInventory=" + trackInventory +
                '}';
    }
}
