package org.de013.productcatalog.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import org.de013.productcatalog.entity.enums.ProductStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_sku", columnList = "sku"),
        @Index(name = "idx_products_status", columnList = "status"),
        @Index(name = "idx_products_featured", columnList = "is_featured"),
        @Index(name = "idx_products_brand", columnList = "brand"),
        @Index(name = "idx_products_price", columnList = "price"),
        @Index(name = "idx_products_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Product extends BaseEntity {

    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must not exceed 255 characters")
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Size(max = 500, message = "Short description must not exceed 500 characters")
    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @NotBlank(message = "SKU is required")
    @Size(max = 100, message = "SKU must not exceed 100 characters")
    @Column(name = "sku", nullable = false, unique = true, length = 100)
    private String sku;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer digits and 2 decimal places")
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @DecimalMin(value = "0.0", message = "Compare price must be non-negative")
    @Digits(integer = 8, fraction = 2, message = "Compare price must have at most 8 integer digits and 2 decimal places")
    @Column(name = "compare_price", precision = 10, scale = 2)
    private BigDecimal comparePrice;

    @DecimalMin(value = "0.0", message = "Cost price must be non-negative")
    @Digits(integer = 8, fraction = 2, message = "Cost price must have at most 8 integer digits and 2 decimal places")
    @Column(name = "cost_price", precision = 10, scale = 2)
    private BigDecimal costPrice;

    @Size(max = 255, message = "Brand must not exceed 255 characters")
    @Column(name = "brand")
    private String brand;

    @DecimalMin(value = "0.0", message = "Weight must be non-negative")
    @Digits(integer = 5, fraction = 3, message = "Weight must have at most 5 integer digits and 3 decimal places")
    @Column(name = "weight", precision = 8, scale = 3)
    private BigDecimal weight;

    @Size(max = 100, message = "Dimensions must not exceed 100 characters")
    @Column(name = "dimensions", length = 100)
    private String dimensions; // e.g., "10x20x30 cm"

    @NotNull(message = "Product status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ProductStatus status = ProductStatus.ACTIVE;

    @Column(name = "is_featured", nullable = false)
    @Builder.Default
    private Boolean isFeatured = false;

    @Column(name = "is_digital", nullable = false)
    @Builder.Default
    private Boolean isDigital = false;

    @Column(name = "requires_shipping", nullable = false)
    @Builder.Default
    private Boolean requiresShipping = true;

    @Size(max = 255, message = "Meta title must not exceed 255 characters")
    @Column(name = "meta_title")
    private String metaTitle;

    @Column(name = "meta_description", columnDefinition = "TEXT")
    private String metaDescription;

    @Column(name = "search_keywords", columnDefinition = "TEXT")
    private String searchKeywords;

    // Relationships
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProductCategory> productCategories = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Inventory inventory;



    // Helper methods
    public boolean isAvailable() {
        return status != null && status.isAvailable();
    }

    public boolean canBePurchased() {
        return isAvailable() && (inventory == null || inventory.isInStock());
    }

    public boolean isOnSale() {
        return comparePrice != null && comparePrice.compareTo(price) > 0;
    }

    public BigDecimal getDiscountAmount() {
        if (isOnSale()) {
            return comparePrice.subtract(price);
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal getDiscountPercentage() {
        if (isOnSale() && comparePrice.compareTo(BigDecimal.ZERO) > 0) {
            return getDiscountAmount()
                    .divide(comparePrice, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        return BigDecimal.ZERO;
    }

    @Override
    public String toString() {
        return "Product{" +
                "id=" + super.getId() +
                ", name='" + name + '\'' +
                ", sku='" + sku + '\'' +
                ", price=" + price +
                ", status=" + status +
                ", isFeatured=" + isFeatured +
                '}';
    }
}
