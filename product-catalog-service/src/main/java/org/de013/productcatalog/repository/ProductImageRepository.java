package org.de013.productcatalog.repository;

import org.de013.productcatalog.entity.ProductImage;
import org.de013.productcatalog.entity.enums.ImageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    // Basic queries
    List<ProductImage> findByProductId(Long productId);
    
    List<ProductImage> findByProductIdAndIsActiveTrue(Long productId);
    
    List<ProductImage> findByProductIdAndIsActiveTrueOrderByDisplayOrderAsc(Long productId);
    
    Page<ProductImage> findByProductId(Long productId, Pageable pageable);

    // Image type queries
    List<ProductImage> findByProductIdAndImageType(Long productId, ImageType imageType);
    
    List<ProductImage> findByProductIdAndImageTypeAndIsActiveTrue(Long productId, ImageType imageType);
    
    Optional<ProductImage> findFirstByProductIdAndImageTypeAndIsActiveTrueOrderByDisplayOrderAsc(Long productId, ImageType imageType);
    
    @Query("SELECT pi FROM ProductImage pi WHERE pi.product.id = :productId AND pi.imageType = 'MAIN' AND pi.isActive = true")
    Optional<ProductImage> findMainImageByProductId(@Param("productId") Long productId);

    // Gallery images
    @Query("SELECT pi FROM ProductImage pi WHERE pi.product.id = :productId AND " +
           "pi.imageType IN ('MAIN', 'GALLERY', 'DETAIL', 'LIFESTYLE') AND pi.isActive = true " +
           "ORDER BY pi.displayOrder ASC")
    List<ProductImage> findGalleryImagesByProductId(@Param("productId") Long productId);

    // Variant-specific images
    List<ProductImage> findByVariantId(Long variantId);
    
    List<ProductImage> findByVariantIdAndIsActiveTrue(Long variantId);
    
    List<ProductImage> findByVariantIdAndIsActiveTrueOrderByDisplayOrderAsc(Long variantId);

    @Query("SELECT pi FROM ProductImage pi WHERE pi.product.id = :productId AND pi.variant.id = :variantId AND pi.isActive = true " +
           "ORDER BY pi.displayOrder ASC")
    List<ProductImage> findByProductIdAndVariantId(@Param("productId") Long productId, @Param("variantId") Long variantId);

    // Images by multiple products
    @Query("SELECT pi FROM ProductImage pi WHERE pi.product.id IN :productIds AND pi.isActive = true " +
           "ORDER BY pi.product.id ASC, pi.displayOrder ASC")
    List<ProductImage> findByProductIds(@Param("productIds") List<Long> productIds);

    // Main images for multiple products
    @Query("SELECT pi FROM ProductImage pi WHERE pi.product.id IN :productIds AND pi.imageType = 'MAIN' AND pi.isActive = true")
    List<ProductImage> findMainImagesByProductIds(@Param("productIds") List<Long> productIds);

    // Images by file format
    List<ProductImage> findByProductIdAndFileFormat(Long productId, String fileFormat);
    
    List<ProductImage> findByFileFormat(String fileFormat);

    // Search images
    @Query("SELECT pi FROM ProductImage pi WHERE pi.product.id = :productId AND " +
           "(LOWER(pi.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(pi.altText) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(pi.description) LIKE LOWER(CONCAT('%', :query, '%'))) AND pi.isActive = true")
    List<ProductImage> searchByProductIdAndQuery(@Param("productId") Long productId, @Param("query") String query);

    // Image statistics
    @Query("SELECT pi.imageType, COUNT(pi) FROM ProductImage pi " +
           "WHERE pi.product.id = :productId AND pi.isActive = true " +
           "GROUP BY pi.imageType")
    List<Object[]> countImagesByType(@Param("productId") Long productId);

    @Query("SELECT COUNT(DISTINCT pi.imageType) FROM ProductImage pi " +
           "WHERE pi.product.id = :productId AND pi.isActive = true")
    long countDistinctImageTypes(@Param("productId") Long productId);

    // Images with specific properties
    @Query("SELECT pi FROM ProductImage pi WHERE pi.product.id = :productId AND pi.title IS NOT NULL AND pi.isActive = true")
    List<ProductImage> findByProductIdWithTitle(@Param("productId") Long productId);

    @Query("SELECT pi FROM ProductImage pi WHERE pi.product.id = :productId AND pi.description IS NOT NULL AND pi.isActive = true")
    List<ProductImage> findByProductIdWithDescription(@Param("productId") Long productId);

    // Images by display order range
    @Query("SELECT pi FROM ProductImage pi WHERE pi.product.id = :productId AND " +
           "pi.displayOrder BETWEEN :minOrder AND :maxOrder AND pi.isActive = true " +
           "ORDER BY pi.displayOrder ASC")
    List<ProductImage> findByProductIdAndDisplayOrderRange(@Param("productId") Long productId, 
                                                          @Param("minOrder") Integer minOrder, 
                                                          @Param("maxOrder") Integer maxOrder);

    // Count queries
    long countByProductId(Long productId);
    
    long countByProductIdAndIsActiveTrue(Long productId);
    
    long countByProductIdAndImageType(Long productId, ImageType imageType);
    
    long countByProductIdAndImageTypeAndIsActiveTrue(Long productId, ImageType imageType);
    
    long countByVariantId(Long variantId);
    
    long countByVariantIdAndIsActiveTrue(Long variantId);

    // Exists queries
    boolean existsByProductIdAndImageTypeAndIsActiveTrue(Long productId, ImageType imageType);
    
    boolean existsByVariantId(Long variantId);
    
    boolean existsByUrl(String url);

    // Bulk operations
    @Modifying
    @Query("UPDATE ProductImage pi SET pi.isActive = :active WHERE pi.product.id = :productId")
    int bulkUpdateActiveStatusByProductId(@Param("productId") Long productId, @Param("active") boolean active);

    @Modifying
    @Query("UPDATE ProductImage pi SET pi.isActive = :active WHERE pi.id IN :imageIds")
    int bulkUpdateActiveStatus(@Param("imageIds") List<Long> imageIds, @Param("active") boolean active);

    @Modifying
    @Query("UPDATE ProductImage pi SET pi.displayOrder = pi.displayOrder + :increment " +
           "WHERE pi.product.id = :productId AND pi.displayOrder >= :fromOrder")
    int bulkUpdateDisplayOrder(@Param("productId") Long productId, 
                              @Param("fromOrder") Integer fromOrder, 
                              @Param("increment") Integer increment);

    @Modifying
    @Query("DELETE FROM ProductImage pi WHERE pi.product.id = :productId")
    int deleteByProductId(@Param("productId") Long productId);

    @Modifying
    @Query("DELETE FROM ProductImage pi WHERE pi.variant.id = :variantId")
    int deleteByVariantId(@Param("variantId") Long variantId);

    // Max display order
    @Query("SELECT COALESCE(MAX(pi.displayOrder), 0) FROM ProductImage pi WHERE pi.product.id = :productId")
    Integer findMaxDisplayOrderByProductId(@Param("productId") Long productId);

    @Query("SELECT COALESCE(MAX(pi.displayOrder), 0) FROM ProductImage pi " +
           "WHERE pi.product.id = :productId AND pi.imageType = :imageType")
    Integer findMaxDisplayOrderByProductIdAndType(@Param("productId") Long productId, @Param("imageType") ImageType imageType);

    // Image URLs for products
    @Query("SELECT pi.product.id, pi.url FROM ProductImage pi " +
           "WHERE pi.product.id IN :productIds AND pi.imageType = 'MAIN' AND pi.isActive = true")
    List<Object[]> findMainImageUrlsByProductIds(@Param("productIds") List<Long> productIds);

    // Products without images
    @Query("SELECT DISTINCT p.id FROM Product p " +
           "LEFT JOIN p.images pi ON pi.isActive = true " +
           "WHERE pi.id IS NULL")
    List<Long> findProductIdsWithoutImages();

    // Products with specific image types
    @Query("SELECT DISTINCT pi.product.id FROM ProductImage pi " +
           "WHERE pi.imageType = :imageType AND pi.isActive = true")
    List<Long> findProductIdsByImageType(@Param("imageType") ImageType imageType);
}
