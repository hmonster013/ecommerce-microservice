package org.de013.productcatalog.repository;

import org.de013.productcatalog.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    // Basic queries - used by core methods
    Optional<Inventory> findByProductId(Long productId);

    // Stock reservation operations - used by core methods
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
}
