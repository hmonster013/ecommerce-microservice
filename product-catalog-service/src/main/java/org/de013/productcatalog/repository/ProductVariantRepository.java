package org.de013.productcatalog.repository;

import org.de013.productcatalog.entity.ProductVariant;
import org.de013.productcatalog.entity.enums.VariantType;
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
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    // Basic queries
    List<ProductVariant> findByProductId(Long productId);
    
    List<ProductVariant> findByProductIdAndIsActiveTrue(Long productId);
    
    List<ProductVariant> findByProductIdAndIsActiveTrueOrderByDisplayOrderAsc(Long productId);
    
    Page<ProductVariant> findByProductId(Long productId, Pageable pageable);

    // Variant type queries
    List<ProductVariant> findByProductIdAndVariantType(Long productId, VariantType variantType);
    
    List<ProductVariant> findByProductIdAndVariantTypeAndIsActiveTrue(Long productId, VariantType variantType);
    
    List<ProductVariant> findByProductIdAndVariantTypeAndIsActiveTrueOrderByDisplayOrderAsc(Long productId, VariantType variantType);

    // SKU queries
    Optional<ProductVariant> findBySku(String sku);
    
    List<ProductVariant> findByProductIdAndSkuIsNotNull(Long productId);

    // Value queries
    List<ProductVariant> findByProductIdAndValue(Long productId, String value);
    
    Optional<ProductVariant> findByProductIdAndVariantTypeAndValue(Long productId, VariantType variantType, String value);

    // Grouped by variant type
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.product.id = :productId AND pv.isActive = true " +
           "ORDER BY pv.variantType ASC, pv.displayOrder ASC")
    List<ProductVariant> findByProductIdGroupedByType(@Param("productId") Long productId);

    // Variants with price adjustments
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.product.id = :productId AND pv.priceAdjustment != 0 AND pv.isActive = true")
    List<ProductVariant> findByProductIdWithPriceAdjustment(@Param("productId") Long productId);

    @Query("SELECT pv FROM ProductVariant pv WHERE pv.product.id = :productId AND pv.priceAdjustment > 0 AND pv.isActive = true")
    List<ProductVariant> findByProductIdWithAdditionalCost(@Param("productId") Long productId);

    @Query("SELECT pv FROM ProductVariant pv WHERE pv.product.id = :productId AND pv.priceAdjustment < 0 AND pv.isActive = true")
    List<ProductVariant> findByProductIdWithDiscount(@Param("productId") Long productId);

    // Variants by multiple products
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.product.id IN :productIds AND pv.isActive = true " +
           "ORDER BY pv.product.id ASC, pv.variantType ASC, pv.displayOrder ASC")
    List<ProductVariant> findByProductIds(@Param("productIds") List<Long> productIds);

    // Variant statistics
    @Query("SELECT pv.variantType, COUNT(pv) FROM ProductVariant pv " +
           "WHERE pv.product.id = :productId AND pv.isActive = true " +
           "GROUP BY pv.variantType")
    List<Object[]> countVariantsByType(@Param("productId") Long productId);

    @Query("SELECT COUNT(DISTINCT pv.variantType) FROM ProductVariant pv " +
           "WHERE pv.product.id = :productId AND pv.isActive = true")
    long countDistinctVariantTypes(@Param("productId") Long productId);

    // Variants with images
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.product.id = :productId AND pv.imageUrl IS NOT NULL AND pv.isActive = true")
    List<ProductVariant> findByProductIdWithImages(@Param("productId") Long productId);

    // Search variants
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.product.id = :productId AND " +
           "(LOWER(pv.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(pv.value) LIKE LOWER(CONCAT('%', :query, '%'))) AND pv.isActive = true")
    List<ProductVariant> searchByProductIdAndQuery(@Param("productId") Long productId, @Param("query") String query);

    // Variant combinations
    @Query("SELECT DISTINCT pv.variantType FROM ProductVariant pv " +
           "WHERE pv.product.id = :productId AND pv.isActive = true " +
           "ORDER BY pv.variantType ASC")
    List<VariantType> findDistinctVariantTypesByProductId(@Param("productId") Long productId);

    @Query("SELECT DISTINCT pv.value FROM ProductVariant pv " +
           "WHERE pv.product.id = :productId AND pv.variantType = :variantType AND pv.isActive = true " +
           "ORDER BY pv.displayOrder ASC")
    List<String> findDistinctValuesByProductIdAndType(@Param("productId") Long productId, @Param("variantType") VariantType variantType);

    // Count queries
    long countByProductId(Long productId);
    
    long countByProductIdAndIsActiveTrue(Long productId);
    
    long countByProductIdAndVariantType(Long productId, VariantType variantType);
    
    long countByProductIdAndVariantTypeAndIsActiveTrue(Long productId, VariantType variantType);

    // Exists queries
    boolean existsBySku(String sku);
    
    boolean existsBySkuAndIdNot(String sku, Long id);
    
    boolean existsByProductIdAndVariantTypeAndValue(Long productId, VariantType variantType, String value);
    
    boolean existsByProductIdAndVariantTypeAndValueAndIdNot(Long productId, VariantType variantType, String value, Long id);

    // Bulk operations
    @Modifying
    @Query("UPDATE ProductVariant pv SET pv.isActive = :active WHERE pv.product.id = :productId")
    int bulkUpdateActiveStatusByProductId(@Param("productId") Long productId, @Param("active") boolean active);

    @Modifying
    @Query("UPDATE ProductVariant pv SET pv.isActive = :active WHERE pv.id IN :variantIds")
    int bulkUpdateActiveStatus(@Param("variantIds") List<Long> variantIds, @Param("active") boolean active);

    @Modifying
    @Query("DELETE FROM ProductVariant pv WHERE pv.product.id = :productId")
    int deleteByProductId(@Param("productId") Long productId);

    // Max display order for product variants
    @Query("SELECT COALESCE(MAX(pv.displayOrder), 0) FROM ProductVariant pv " +
           "WHERE pv.product.id = :productId AND pv.variantType = :variantType")
    Integer findMaxDisplayOrderByProductIdAndType(@Param("productId") Long productId, @Param("variantType") VariantType variantType);

    @Query("SELECT COALESCE(MAX(pv.displayOrder), 0) FROM ProductVariant pv WHERE pv.product.id = :productId")
    Integer findMaxDisplayOrderByProductId(@Param("productId") Long productId);

    // Popular variants (most used values)
    @Query("SELECT pv.variantType, pv.value, COUNT(pv) as usage_count FROM ProductVariant pv " +
           "WHERE pv.isActive = true " +
           "GROUP BY pv.variantType, pv.value " +
           "ORDER BY usage_count DESC")
    Page<Object[]> findPopularVariantValues(Pageable pageable);

    @Query("SELECT pv.value, COUNT(pv) as usage_count FROM ProductVariant pv " +
           "WHERE pv.variantType = :variantType AND pv.isActive = true " +
           "GROUP BY pv.value " +
           "ORDER BY usage_count DESC")
    Page<Object[]> findPopularValuesByType(@Param("variantType") VariantType variantType, Pageable pageable);
}
