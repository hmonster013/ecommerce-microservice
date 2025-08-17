package org.de013.productcatalog.repository;

import org.de013.productcatalog.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    // Basic queries
    List<ProductCategory> findByProductId(Long productId);
    
    List<ProductCategory> findByCategoryId(Long categoryId);
    
    Optional<ProductCategory> findByProductIdAndCategoryId(Long productId, Long categoryId);

    // Primary category queries
    Optional<ProductCategory> findByProductIdAndIsPrimaryTrue(Long productId);
    
    List<ProductCategory> findByCategoryIdAndIsPrimaryTrue(Long categoryId);

    // Bulk queries
    List<ProductCategory> findByProductIdIn(List<Long> productIds);
    
    List<ProductCategory> findByCategoryIdIn(List<Long> categoryIds);

    @Query("SELECT pc FROM ProductCategory pc WHERE pc.product.id IN :productIds AND pc.isPrimary = true")
    List<ProductCategory> findPrimaryCategoriesByProductIds(@Param("productIds") List<Long> productIds);

    // Category with product count
    @Query("SELECT pc.category.id, pc.category.name, COUNT(pc.product) FROM ProductCategory pc " +
           "JOIN pc.product p ON p.status = 'ACTIVE' " +
           "GROUP BY pc.category.id, pc.category.name " +
           "ORDER BY COUNT(pc.product) DESC")
    List<Object[]> findCategoriesWithActiveProductCount();

    @Query("SELECT pc.category.id, pc.category.name, COUNT(pc.product) FROM ProductCategory pc " +
           "JOIN pc.product p ON p.status = 'ACTIVE' " +
           "WHERE pc.category.id IN :categoryIds " +
           "GROUP BY pc.category.id, pc.category.name")
    List<Object[]> findCategoriesWithActiveProductCount(@Param("categoryIds") List<Long> categoryIds);

    // Product categories with category details
    @Query("SELECT pc, c FROM ProductCategory pc " +
           "JOIN pc.category c " +
           "WHERE pc.product.id = :productId " +
           "ORDER BY pc.isPrimary DESC, c.name ASC")
    List<Object[]> findProductCategoriesWithDetails(@Param("productId") Long productId);

    @Query("SELECT pc, c FROM ProductCategory pc " +
           "JOIN pc.category c " +
           "WHERE pc.product.id IN :productIds " +
           "ORDER BY pc.product.id, pc.isPrimary DESC, c.name ASC")
    List<Object[]> findProductCategoriesWithDetails(@Param("productIds") List<Long> productIds);

    // Count queries
    long countByProductId(Long productId);
    
    long countByCategoryId(Long categoryId);
    
    long countByProductIdAndIsPrimaryTrue(Long productId);
    
    long countByCategoryIdAndIsPrimaryTrue(Long categoryId);

    // Exists queries
    boolean existsByProductIdAndCategoryId(Long productId, Long categoryId);
    
    boolean existsByProductIdAndIsPrimaryTrue(Long productId);

    // Bulk operations
    @Modifying
    @Query("DELETE FROM ProductCategory pc WHERE pc.product.id = :productId")
    int deleteByProductId(@Param("productId") Long productId);

    @Modifying
    @Query("DELETE FROM ProductCategory pc WHERE pc.category.id = :categoryId")
    int deleteByCategoryId(@Param("categoryId") Long categoryId);

    @Modifying
    @Query("DELETE FROM ProductCategory pc WHERE pc.product.id = :productId AND pc.category.id IN :categoryIds")
    int deleteByProductIdAndCategoryIds(@Param("productId") Long productId, @Param("categoryIds") List<Long> categoryIds);

    @Modifying
    @Query("UPDATE ProductCategory pc SET pc.isPrimary = false WHERE pc.product.id = :productId")
    int clearPrimaryCategory(@Param("productId") Long productId);

    @Modifying
    @Query("UPDATE ProductCategory pc SET pc.isPrimary = false WHERE pc.product.id = :productId AND pc.category.id != :categoryId")
    int clearOtherPrimaryCategories(@Param("productId") Long productId, @Param("categoryId") Long categoryId);

    // Category hierarchy queries
    @Query("SELECT DISTINCT pc.product.id FROM ProductCategory pc " +
           "WHERE pc.category.id IN (" +
           "  SELECT c.id FROM Category c WHERE c.id = :categoryId " +
           "  UNION " +
           "  SELECT c.id FROM Category c WHERE c.parent.id = :categoryId" +
           ")")
    List<Long> findProductIdsByCategoryAndChildren(@Param("categoryId") Long categoryId);

    // Products in multiple categories
    @Query("SELECT pc.product.id FROM ProductCategory pc " +
           "WHERE pc.category.id IN :categoryIds " +
           "GROUP BY pc.product.id " +
           "HAVING COUNT(DISTINCT pc.category.id) = :categoryCount")
    List<Long> findProductIdsInAllCategories(@Param("categoryIds") List<Long> categoryIds, 
                                           @Param("categoryCount") long categoryCount);

    @Query("SELECT pc.product.id FROM ProductCategory pc " +
           "WHERE pc.category.id IN :categoryIds " +
           "GROUP BY pc.product.id " +
           "HAVING COUNT(DISTINCT pc.category.id) >= :minCategoryCount")
    List<Long> findProductIdsInMinimumCategories(@Param("categoryIds") List<Long> categoryIds, 
                                                @Param("minCategoryCount") long minCategoryCount);

    // Category statistics
    @Query("SELECT pc.category.id, " +
           "COUNT(pc.product) as total_products, " +
           "COUNT(CASE WHEN pc.isPrimary = true THEN 1 END) as primary_products " +
           "FROM ProductCategory pc " +
           "JOIN pc.product p ON p.status = 'ACTIVE' " +
           "GROUP BY pc.category.id")
    List<Object[]> findCategoryStatistics();

    // Related categories (categories that share products)
    @Query("SELECT pc2.category.id, pc2.category.name, COUNT(DISTINCT pc2.product.id) as shared_products " +
           "FROM ProductCategory pc1 " +
           "JOIN ProductCategory pc2 ON pc1.product.id = pc2.product.id " +
           "WHERE pc1.category.id = :categoryId AND pc2.category.id != :categoryId " +
           "GROUP BY pc2.category.id, pc2.category.name " +
           "ORDER BY shared_products DESC")
    List<Object[]> findRelatedCategories(@Param("categoryId") Long categoryId);
}
