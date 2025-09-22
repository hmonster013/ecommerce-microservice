package org.de013.productcatalog.repository;

import org.de013.productcatalog.entity.VariantInventory;
import org.de013.productcatalog.entity.enums.VariantType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VariantInventoryRepository extends JpaRepository<VariantInventory, Long> {

    // Basic queries
    Optional<VariantInventory> findByVariantId(Long variantId);
    
    List<VariantInventory> findByProductId(Long productId);
    
    List<VariantInventory> findByProductIdAndTrackInventoryTrue(Long productId);
    
    Optional<VariantInventory> findBySku(String sku);

    // Stock status queries
    @Query("SELECT vi FROM VariantInventory vi WHERE vi.product.id = :productId AND vi.quantity > 0")
    List<VariantInventory> findInStockByProductId(@Param("productId") Long productId);
    
    @Query("SELECT vi FROM VariantInventory vi WHERE vi.product.id = :productId AND vi.quantity = 0")
    List<VariantInventory> findOutOfStockByProductId(@Param("productId") Long productId);
    
    @Query("SELECT vi FROM VariantInventory vi WHERE vi.product.id = :productId AND vi.quantity <= vi.minStockLevel")
    List<VariantInventory> findLowStockByProductId(@Param("productId") Long productId);
    
    @Query("SELECT vi FROM VariantInventory vi WHERE vi.product.id = :productId AND vi.quantity <= vi.reorderPoint")
    List<VariantInventory> findNeedsReorderByProductId(@Param("productId") Long productId);

    // Variant type specific queries
    @Query("SELECT vi FROM VariantInventory vi WHERE vi.product.id = :productId AND vi.variant.variantType = :variantType")
    List<VariantInventory> findByProductIdAndVariantType(@Param("productId") Long productId, @Param("variantType") VariantType variantType);
    
    @Query("SELECT vi FROM VariantInventory vi WHERE vi.product.id = :productId AND vi.variant.variantType = :variantType AND vi.quantity > 0")
    List<VariantInventory> findInStockByProductIdAndVariantType(@Param("productId") Long productId, @Param("variantType") VariantType variantType);

    // Available quantity queries (quantity - reserved_quantity)
    @Query("SELECT vi FROM VariantInventory vi WHERE vi.product.id = :productId AND (vi.quantity - vi.reservedQuantity) > 0")
    List<VariantInventory> findAvailableByProductId(@Param("productId") Long productId);
    
    @Query("SELECT vi FROM VariantInventory vi WHERE vi.variant.id = :variantId AND (vi.quantity - vi.reservedQuantity) >= :requestedQuantity")
    Optional<VariantInventory> findAvailableByVariantId(@Param("variantId") Long variantId, @Param("requestedQuantity") Integer requestedQuantity);

    // Stock reservation operations
    @Modifying
    @Query("UPDATE VariantInventory vi SET vi.reservedQuantity = vi.reservedQuantity + :quantity " +
           "WHERE vi.variant.id = :variantId AND (vi.quantity - vi.reservedQuantity) >= :quantity")
    int reserveStock(@Param("variantId") Long variantId, @Param("quantity") Integer quantity);

    @Modifying
    @Query("UPDATE VariantInventory vi SET vi.reservedQuantity = vi.reservedQuantity - :quantity " +
           "WHERE vi.variant.id = :variantId AND vi.reservedQuantity >= :quantity")
    int releaseReservedStock(@Param("variantId") Long variantId, @Param("quantity") Integer quantity);

    @Modifying
    @Query("UPDATE VariantInventory vi SET vi.quantity = vi.quantity - :quantity, vi.reservedQuantity = vi.reservedQuantity - :quantity " +
           "WHERE vi.variant.id = :variantId AND vi.reservedQuantity >= :quantity")
    int fulfillOrder(@Param("variantId") Long variantId, @Param("quantity") Integer quantity);

    // Bulk operations
    @Modifying
    @Query("UPDATE VariantInventory vi SET vi.quantity = vi.quantity + :quantity WHERE vi.variant.id IN :variantIds")
    int bulkAddStock(@Param("variantIds") List<Long> variantIds, @Param("quantity") Integer quantity);
    
    @Modifying
    @Query("UPDATE VariantInventory vi SET vi.quantity = :quantity WHERE vi.variant.id IN :variantIds")
    int bulkSetStock(@Param("variantIds") List<Long> variantIds, @Param("quantity") Integer quantity);

    // Statistics queries
    @Query("SELECT SUM(vi.quantity) FROM VariantInventory vi WHERE vi.product.id = :productId")
    Long getTotalQuantityByProductId(@Param("productId") Long productId);
    
    @Query("SELECT SUM(vi.quantity - vi.reservedQuantity) FROM VariantInventory vi WHERE vi.product.id = :productId")
    Long getTotalAvailableQuantityByProductId(@Param("productId") Long productId);
    
    @Query("SELECT COUNT(vi) FROM VariantInventory vi WHERE vi.product.id = :productId AND vi.quantity > 0")
    Long countInStockVariantsByProductId(@Param("productId") Long productId);
    
    @Query("SELECT COUNT(vi) FROM VariantInventory vi WHERE vi.product.id = :productId AND vi.quantity = 0")
    Long countOutOfStockVariantsByProductId(@Param("productId") Long productId);

    // Location-based queries
    List<VariantInventory> findByLocation(String location);
    
    @Query("SELECT DISTINCT vi.location FROM VariantInventory vi WHERE vi.location IS NOT NULL ORDER BY vi.location")
    List<String> findDistinctLocations();

    // Advanced queries
    @Query("SELECT vi FROM VariantInventory vi WHERE vi.product.id = :productId " +
           "AND vi.variant.variantType = :variantType AND vi.variant.value = :value")
    Optional<VariantInventory> findByProductIdAndVariantTypeAndValue(
            @Param("productId") Long productId, 
            @Param("variantType") VariantType variantType, 
            @Param("value") String value);

    // Check if any variant of a product is in stock
    @Query("SELECT CASE WHEN COUNT(vi) > 0 THEN true ELSE false END FROM VariantInventory vi " +
           "WHERE vi.product.id = :productId AND (vi.quantity - vi.reservedQuantity) > 0")
    boolean hasAnyVariantInStock(@Param("productId") Long productId);

    // Get variants that can fulfill a specific quantity
    @Query("SELECT vi FROM VariantInventory vi WHERE vi.product.id = :productId " +
           "AND (vi.quantity - vi.reservedQuantity) >= :requestedQuantity")
    List<VariantInventory> findVariantsThatCanFulfill(@Param("productId") Long productId, @Param("requestedQuantity") Integer requestedQuantity);

    // Delete operations
    @Modifying
    @Query("DELETE FROM VariantInventory vi WHERE vi.variant.id = :variantId")
    int deleteByVariantId(@Param("variantId") Long variantId);
    
    @Modifying
    @Query("DELETE FROM VariantInventory vi WHERE vi.product.id = :productId")
    int deleteByProductId(@Param("productId") Long productId);
}
