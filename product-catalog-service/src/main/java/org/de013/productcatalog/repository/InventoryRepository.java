package org.de013.productcatalog.repository;

import org.de013.productcatalog.entity.Inventory;
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
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    // Basic queries
    Optional<Inventory> findByProductId(Long productId);
    
    List<Inventory> findByProductIdIn(List<Long> productIds);

    // Stock level queries
    @Query("SELECT i FROM Inventory i WHERE (i.quantity - i.reservedQuantity) > 0 AND i.trackInventory = true")
    List<Inventory> findInStockInventories();

    @Query("SELECT i FROM Inventory i WHERE (i.quantity - i.reservedQuantity) > 0 AND i.trackInventory = true")
    Page<Inventory> findInStockInventories(Pageable pageable);

    @Query("SELECT i FROM Inventory i WHERE (i.quantity - i.reservedQuantity) <= 0 AND i.trackInventory = true")
    List<Inventory> findOutOfStockInventories();

    @Query("SELECT i FROM Inventory i WHERE (i.quantity - i.reservedQuantity) <= 0 AND i.trackInventory = true")
    Page<Inventory> findOutOfStockInventories(Pageable pageable);

    // Low stock queries
    @Query("SELECT i FROM Inventory i WHERE (i.quantity - i.reservedQuantity) <= i.minStockLevel AND " +
           "(i.quantity - i.reservedQuantity) > 0 AND i.trackInventory = true")
    List<Inventory> findLowStockInventories();

    @Query("SELECT i FROM Inventory i WHERE (i.quantity - i.reservedQuantity) <= i.minStockLevel AND " +
           "(i.quantity - i.reservedQuantity) > 0 AND i.trackInventory = true")
    Page<Inventory> findLowStockInventories(Pageable pageable);

    // Reorder queries
    @Query("SELECT i FROM Inventory i WHERE (i.quantity - i.reservedQuantity) <= i.reorderPoint AND i.trackInventory = true")
    List<Inventory> findInventoriesNeedingReorder();

    @Query("SELECT i FROM Inventory i WHERE (i.quantity - i.reservedQuantity) <= i.reorderPoint AND i.trackInventory = true")
    Page<Inventory> findInventoriesNeedingReorder(Pageable pageable);

    // Quantity range queries
    @Query("SELECT i FROM Inventory i WHERE i.quantity BETWEEN :minQuantity AND :maxQuantity")
    List<Inventory> findByQuantityRange(@Param("minQuantity") Integer minQuantity, @Param("maxQuantity") Integer maxQuantity);

    @Query("SELECT i FROM Inventory i WHERE (i.quantity - i.reservedQuantity) BETWEEN :minAvailable AND :maxAvailable")
    List<Inventory> findByAvailableQuantityRange(@Param("minAvailable") Integer minAvailable, @Param("maxAvailable") Integer maxAvailable);

    // Reserved quantity queries
    @Query("SELECT i FROM Inventory i WHERE i.reservedQuantity > 0")
    List<Inventory> findWithReservedQuantity();

    @Query("SELECT i FROM Inventory i WHERE i.reservedQuantity >= :minReserved")
    List<Inventory> findWithReservedQuantityGreaterThan(@Param("minReserved") Integer minReserved);

    // Location-based queries
    List<Inventory> findByLocation(String location);
    
    List<Inventory> findByLocationContainingIgnoreCase(String locationPattern);

    // Supplier queries
    List<Inventory> findBySupplierSku(String supplierSku);
    
    List<Inventory> findBySupplierSkuContainingIgnoreCase(String supplierSkuPattern);

    // Track inventory queries
    List<Inventory> findByTrackInventoryTrue();
    
    List<Inventory> findByTrackInventoryFalse();

    // Allow backorder queries
    List<Inventory> findByAllowBackorderTrue();
    
    List<Inventory> findByAllowBackorderFalse();

    // Combined queries
    @Query("SELECT i FROM Inventory i WHERE i.trackInventory = :trackInventory AND i.allowBackorder = :allowBackorder")
    List<Inventory> findByTrackInventoryAndAllowBackorder(@Param("trackInventory") Boolean trackInventory, 
                                                         @Param("allowBackorder") Boolean allowBackorder);

    // Inventory with product information
    @Query("SELECT i, p FROM Inventory i " +
           "JOIN i.product p " +
           "WHERE (i.quantity - i.reservedQuantity) <= i.minStockLevel AND i.trackInventory = true " +
           "ORDER BY (i.quantity - i.reservedQuantity) ASC")
    List<Object[]> findLowStockInventoriesWithProduct();

    @Query("SELECT i, p FROM Inventory i " +
           "JOIN i.product p " +
           "WHERE (i.quantity - i.reservedQuantity) <= 0 AND i.trackInventory = true")
    List<Object[]> findOutOfStockInventoriesWithProduct();

    @Query("SELECT i, p FROM Inventory i " +
           "JOIN i.product p " +
           "WHERE (i.quantity - i.reservedQuantity) <= i.reorderPoint AND i.trackInventory = true " +
           "ORDER BY (i.quantity - i.reservedQuantity) ASC")
    List<Object[]> findReorderInventoriesWithProduct();

    // Statistics queries
    @Query("SELECT COUNT(i) FROM Inventory i WHERE (i.quantity - i.reservedQuantity) > 0 AND i.trackInventory = true")
    long countInStockInventories();

    @Query("SELECT COUNT(i) FROM Inventory i WHERE (i.quantity - i.reservedQuantity) <= 0 AND i.trackInventory = true")
    long countOutOfStockInventories();

    @Query("SELECT COUNT(i) FROM Inventory i WHERE (i.quantity - i.reservedQuantity) <= i.minStockLevel AND " +
           "(i.quantity - i.reservedQuantity) > 0 AND i.trackInventory = true")
    long countLowStockInventories();

    @Query("SELECT COUNT(i) FROM Inventory i WHERE (i.quantity - i.reservedQuantity) <= i.reorderPoint AND i.trackInventory = true")
    long countInventoriesNeedingReorder();

    // Aggregation queries
    @Query("SELECT SUM(i.quantity) FROM Inventory i WHERE i.trackInventory = true")
    Long getTotalQuantity();

    @Query("SELECT SUM(i.reservedQuantity) FROM Inventory i WHERE i.trackInventory = true")
    Long getTotalReservedQuantity();

    @Query("SELECT SUM(i.quantity - i.reservedQuantity) FROM Inventory i WHERE i.trackInventory = true")
    Long getTotalAvailableQuantity();

    @Query("SELECT AVG(i.quantity) FROM Inventory i WHERE i.trackInventory = true")
    Double getAverageQuantity();

    // Bulk operations
    @Modifying
    @Query("UPDATE Inventory i SET i.quantity = i.quantity + :adjustment WHERE i.product.id IN :productIds")
    int bulkAdjustQuantity(@Param("productIds") List<Long> productIds, @Param("adjustment") Integer adjustment);

    @Modifying
    @Query("UPDATE Inventory i SET i.reservedQuantity = i.reservedQuantity + :adjustment WHERE i.product.id IN :productIds")
    int bulkAdjustReservedQuantity(@Param("productIds") List<Long> productIds, @Param("adjustment") Integer adjustment);

    @Modifying
    @Query("UPDATE Inventory i SET i.minStockLevel = :minStockLevel WHERE i.product.id IN :productIds")
    int bulkUpdateMinStockLevel(@Param("productIds") List<Long> productIds, @Param("minStockLevel") Integer minStockLevel);

    @Modifying
    @Query("UPDATE Inventory i SET i.reorderPoint = :reorderPoint WHERE i.product.id IN :productIds")
    int bulkUpdateReorderPoint(@Param("productIds") List<Long> productIds, @Param("reorderPoint") Integer reorderPoint);

    @Modifying
    @Query("UPDATE Inventory i SET i.trackInventory = :trackInventory WHERE i.product.id IN :productIds")
    int bulkUpdateTrackInventory(@Param("productIds") List<Long> productIds, @Param("trackInventory") Boolean trackInventory);

    @Modifying
    @Query("UPDATE Inventory i SET i.allowBackorder = :allowBackorder WHERE i.product.id IN :productIds")
    int bulkUpdateAllowBackorder(@Param("productIds") List<Long> productIds, @Param("allowBackorder") Boolean allowBackorder);

    // Stock reservation operations
    @Modifying
    @Query("UPDATE Inventory i SET i.reservedQuantity = i.reservedQuantity + :quantity " +
           "WHERE i.product.id = :productId AND (i.quantity - i.reservedQuantity) >= :quantity")
    int reserveStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Modifying
    @Query("UPDATE Inventory i SET i.reservedQuantity = i.reservedQuantity - :quantity " +
           "WHERE i.product.id = :productId AND i.reservedQuantity >= :quantity")
    int releaseReservedStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Modifying
    @Query("UPDATE Inventory i SET i.quantity = i.quantity - :quantity, i.reservedQuantity = i.reservedQuantity - :quantity " +
           "WHERE i.product.id = :productId AND i.reservedQuantity >= :quantity")
    int fulfillOrder(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    // Inventory alerts
    @Query("SELECT i.product.id, i.product.name, i.product.sku, i.quantity, i.reservedQuantity, " +
           "(i.quantity - i.reservedQuantity) as available, i.minStockLevel " +
           "FROM Inventory i " +
           "JOIN i.product p " +
           "WHERE (i.quantity - i.reservedQuantity) <= i.minStockLevel AND i.trackInventory = true " +
           "ORDER BY (i.quantity - i.reservedQuantity) ASC")
    List<Object[]> getStockAlerts();

    // Inventory by location statistics
    @Query("SELECT i.location, COUNT(i), SUM(i.quantity), SUM(i.reservedQuantity) " +
           "FROM Inventory i " +
           "WHERE i.location IS NOT NULL " +
           "GROUP BY i.location " +
           "ORDER BY i.location")
    List<Object[]> getInventoryStatsByLocation();
}
