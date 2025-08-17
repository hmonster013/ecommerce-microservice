package org.de013.productcatalog.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.de013.productcatalog.entity.enums.VariantType;

import java.math.BigDecimal;

@Entity
@Table(name = "product_variants", indexes = {
        @Index(name = "idx_product_variants_product_id", columnList = "product_id"),
        @Index(name = "idx_product_variants_sku", columnList = "sku"),
        @Index(name = "idx_product_variants_type", columnList = "variant_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Product is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @NotNull(message = "Variant type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "variant_type", nullable = false)
    private VariantType variantType;

    @NotBlank(message = "Variant name is required")
    @Size(max = 255, message = "Variant name must not exceed 255 characters")
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank(message = "Variant value is required")
    @Size(max = 255, message = "Variant value must not exceed 255 characters")
    @Column(name = "value", nullable = false)
    private String value;

    @DecimalMin(value = "0.0", message = "Price adjustment must be non-negative")
    @Digits(integer = 8, fraction = 2, message = "Price adjustment must have at most 8 integer digits and 2 decimal places")
    @Column(name = "price_adjustment", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal priceAdjustment = BigDecimal.ZERO;

    @Size(max = 100, message = "Variant SKU must not exceed 100 characters")
    @Column(name = "sku", length = 100, unique = true)
    private String sku;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Size(max = 255, message = "Image URL must not exceed 255 characters")
    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Helper methods
    public BigDecimal getEffectivePrice() {
        if (product != null && product.getPrice() != null) {
            return product.getPrice().add(priceAdjustment != null ? priceAdjustment : BigDecimal.ZERO);
        }
        return priceAdjustment != null ? priceAdjustment : BigDecimal.ZERO;
    }

    public boolean hasAdditionalCost() {
        return priceAdjustment != null && priceAdjustment.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasDiscount() {
        return priceAdjustment != null && priceAdjustment.compareTo(BigDecimal.ZERO) < 0;
    }

    public String getFullName() {
        if (name != null && value != null) {
            return name + ": " + value;
        } else if (value != null) {
            return value;
        } else if (name != null) {
            return name;
        }
        return "";
    }

    @Override
    public String toString() {
        return "ProductVariant{" +
                "id=" + id +
                ", variantType=" + variantType +
                ", name='" + name + '\'' +
                ", value='" + value + '\'' +
                ", priceAdjustment=" + priceAdjustment +
                ", isActive=" + isActive +
                '}';
    }
}
