package org.de013.productcatalog.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_categories", 
       indexes = {
           @Index(name = "idx_product_categories_product_id", columnList = "product_id"),
           @Index(name = "idx_product_categories_category_id", columnList = "category_id"),
           @Index(name = "idx_product_categories_primary", columnList = "is_primary")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_product_category", columnNames = {"product_id", "category_id"})
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductCategory extends BaseEntity {

    @NotNull(message = "Product is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @NotNull(message = "Category is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Helper methods
    public void setPrimary(boolean primary) {
        this.isPrimary = primary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductCategory)) return false;
        ProductCategory that = (ProductCategory) o;
        return product != null && category != null &&
               product.getId() != null && category.getId() != null &&
               product.getId().equals(that.product.getId()) &&
               category.getId().equals(that.category.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ProductCategory{" +
                "id=" + super.getId() +
                ", productId=" + (product != null ? product.getId() : null) +
                ", categoryId=" + (category != null ? category.getId() : null) +
                ", isPrimary=" + isPrimary +
                '}';
    }
}
