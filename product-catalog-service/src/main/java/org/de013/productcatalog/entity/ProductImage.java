package org.de013.productcatalog.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.de013.productcatalog.entity.enums.ImageType;

@Entity
@Table(name = "product_images", indexes = {
        @Index(name = "idx_product_images_product_id", columnList = "product_id"),
        @Index(name = "idx_product_images_type", columnList = "image_type"),
        @Index(name = "idx_product_images_display_order", columnList = "display_order"),
        @Index(name = "idx_product_images_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Product is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @NotBlank(message = "Image URL is required")
    @Size(max = 500, message = "Image URL must not exceed 500 characters")
    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Size(max = 255, message = "Alt text must not exceed 255 characters")
    @Column(name = "alt_text")
    private String altText;

    @NotNull(message = "Image type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "image_type", nullable = false)
    @Builder.Default
    private ImageType imageType = ImageType.GALLERY;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Column(name = "title")
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Size(max = 100, message = "File size must not exceed 100 characters")
    @Column(name = "file_size", length = 100)
    private String fileSize; // e.g., "2.5MB"

    @Size(max = 50, message = "Dimensions must not exceed 50 characters")
    @Column(name = "dimensions", length = 50)
    private String dimensions; // e.g., "1920x1080"

    @Size(max = 10, message = "File format must not exceed 10 characters")
    @Column(name = "file_format", length = 10)
    private String fileFormat; // e.g., "JPG", "PNG"

    // Optional: Link to specific variant if this image is variant-specific
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    // Helper methods
    public boolean isPrimary() {
        return imageType != null && imageType.isPrimary();
    }

    public boolean isDisplayInGallery() {
        return imageType != null && imageType.isDisplayInGallery();
    }

    public String getDisplayName() {
        if (title != null && !title.trim().isEmpty()) {
            return title;
        }
        if (altText != null && !altText.trim().isEmpty()) {
            return altText;
        }
        return imageType != null ? imageType.getDisplayName() : "Product Image";
    }

    @Override
    public String toString() {
        return "ProductImage{" +
                "id=" + id +
                ", url='" + url + '\'' +
                ", imageType=" + imageType +
                ", displayOrder=" + displayOrder +
                ", isActive=" + isActive +
                '}';
    }
}
