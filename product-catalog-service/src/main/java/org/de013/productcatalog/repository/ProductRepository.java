package org.de013.productcatalog.repository;

import org.de013.productcatalog.entity.Product;
import org.de013.productcatalog.entity.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    // Basic queries
    Optional<Product> findBySku(String sku);
    
    List<Product> findByStatus(ProductStatus status);
    
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);
    
    List<Product> findByIsFeaturedTrue();
    
    Page<Product> findByIsFeaturedTrue(Pageable pageable);
    
    List<Product> findByBrand(String brand);
    
    Page<Product> findByBrand(String brand, Pageable pageable);

    // Price range queries
    List<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);
    
    Page<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);
    
    List<Product> findByPriceGreaterThanEqual(BigDecimal minPrice);
    
    List<Product> findByPriceLessThanEqual(BigDecimal maxPrice);

    // Category-based queries
    @Query("SELECT DISTINCT p FROM Product p " +
           "JOIN p.productCategories pc " +
           "WHERE pc.category.id = :categoryId AND p.status = :status")
    List<Product> findByCategoryIdAndStatus(@Param("categoryId") Long categoryId, 
                                          @Param("status") ProductStatus status);

    @Query("SELECT DISTINCT p FROM Product p " +
           "JOIN p.productCategories pc " +
           "WHERE pc.category.id = :categoryId AND p.status = :status")
    Page<Product> findByCategoryIdAndStatus(@Param("categoryId") Long categoryId, 
                                          @Param("status") ProductStatus status, 
                                          Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p " +
           "JOIN p.productCategories pc " +
           "WHERE pc.category.id IN :categoryIds AND p.status = :status")
    Page<Product> findByCategoryIdsAndStatus(@Param("categoryIds") List<Long> categoryIds, 
                                           @Param("status") ProductStatus status, 
                                           Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p " +
           "JOIN p.productCategories pc " +
           "WHERE pc.category.slug = :categorySlug AND p.status = :status")
    Page<Product> findByCategorySlugAndStatus(@Param("categorySlug") String categorySlug, 
                                            @Param("status") ProductStatus status, 
                                            Pageable pageable);

    // Search queries
    @Query("SELECT p FROM Product p WHERE " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.brand) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.searchKeywords) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "p.status = :status")
    Page<Product> searchByQuery(@Param("query") String query, 
                               @Param("status") ProductStatus status, 
                               Pageable pageable);

    // Full-text search using PostgreSQL
    @Query(value = "SELECT p.* FROM products p WHERE " +
                   "to_tsvector('english', p.name || ' ' || COALESCE(p.description, '') || ' ' || " +
                   "COALESCE(p.brand, '') || ' ' || COALESCE(p.search_keywords, '')) " +
                   "@@ plainto_tsquery('english', :query) AND p.status = CAST(:status AS product_status) " +
                   "ORDER BY ts_rank(to_tsvector('english', p.name || ' ' || COALESCE(p.description, '') || ' ' || " +
                   "COALESCE(p.brand, '') || ' ' || COALESCE(p.search_keywords, '')), " +
                   "plainto_tsquery('english', :query)) DESC",
           nativeQuery = true)
    Page<Product> fullTextSearch(@Param("query") String query, 
                                @Param("status") String status, 
                                Pageable pageable);

    // Featured products with category
    @Query("SELECT DISTINCT p FROM Product p " +
           "JOIN p.productCategories pc " +
           "WHERE p.isFeatured = true AND pc.category.id = :categoryId AND p.status = :status")
    List<Product> findFeaturedByCategoryId(@Param("categoryId") Long categoryId, 
                                         @Param("status") ProductStatus status);

    // Products with inventory
    @Query("SELECT p FROM Product p " +
           "JOIN p.inventory i " +
           "WHERE i.quantity > 0 AND p.status = :status")
    Page<Product> findInStockProducts(@Param("status") ProductStatus status, Pageable pageable);

    @Query("SELECT p FROM Product p " +
           "JOIN p.inventory i " +
           "WHERE i.quantity <= i.minStockLevel AND p.status = :status")
    List<Product> findLowStockProducts(@Param("status") ProductStatus status);

    // Products on sale
    @Query("SELECT p FROM Product p WHERE p.comparePrice > p.price AND p.status = :status")
    Page<Product> findProductsOnSale(@Param("status") ProductStatus status, Pageable pageable);

    // Recently added products
    @Query("SELECT p FROM Product p WHERE p.status = :status ORDER BY p.createdAt DESC")
    Page<Product> findRecentProducts(@Param("status") ProductStatus status, Pageable pageable);

    // Products by multiple brands
    Page<Product> findByBrandInAndStatus(List<String> brands, ProductStatus status, Pageable pageable);

    // Count queries
    long countByStatus(ProductStatus status);
    
    long countByIsFeaturedTrue();
    
    @Query("SELECT COUNT(DISTINCT p) FROM Product p " +
           "JOIN p.productCategories pc " +
           "WHERE pc.category.id = :categoryId AND p.status = :status")
    long countByCategoryIdAndStatus(@Param("categoryId") Long categoryId, @Param("status") ProductStatus status);

    // Bulk operations
    @Modifying
    @Query("UPDATE Product p SET p.status = :newStatus WHERE p.status = :oldStatus")
    int bulkUpdateStatus(@Param("oldStatus") ProductStatus oldStatus, @Param("newStatus") ProductStatus newStatus);

    @Modifying
    @Query("UPDATE Product p SET p.isFeatured = :featured WHERE p.id IN :productIds")
    int bulkUpdateFeatured(@Param("productIds") List<Long> productIds, @Param("featured") boolean featured);

    // Exists queries
    boolean existsBySku(String sku);
    
    boolean existsBySkuAndIdNot(String sku, Long id);

    // Related products (same category, different product)
    @Query("SELECT DISTINCT p2 FROM Product p1 " +
           "JOIN p1.productCategories pc1 " +
           "JOIN pc1.category c " +
           "JOIN c.productCategories pc2 " +
           "JOIN pc2.product p2 " +
           "WHERE p1.id = :productId AND p2.id != :productId AND p2.status = :status " +
           "ORDER BY p2.createdAt DESC")
    Page<Product> findRelatedProducts(@Param("productId") Long productId, 
                                    @Param("status") ProductStatus status, 
                                    Pageable pageable);
}
