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
    @Query(value = "SELECT DISTINCT p.* FROM products p " +
           "JOIN product_categories pc ON p.id = pc.product_id " +
           "WHERE pc.category_id = :categoryId AND p.status = CAST(:status AS product_status)",
           nativeQuery = true)
    List<Product> findByCategoryIdAndStatus(@Param("categoryId") Long categoryId,
                                          @Param("status") String status);

    @Query(value = "SELECT DISTINCT p.* FROM products p " +
           "JOIN product_categories pc ON p.id = pc.product_id " +
           "WHERE pc.category_id = :categoryId AND p.status = CAST(:status AS product_status)",
           nativeQuery = true)
    Page<Product> findByCategoryIdAndStatus(@Param("categoryId") Long categoryId,
                                          @Param("status") String status,
                                          Pageable pageable);

    @Query(value = "SELECT DISTINCT p.* FROM products p " +
           "JOIN product_categories pc ON p.id = pc.product_id " +
           "WHERE pc.category_id IN :categoryIds AND p.status = CAST(:status AS product_status)",
           nativeQuery = true)
    Page<Product> findByCategoryIdsAndStatus(@Param("categoryIds") List<Long> categoryIds,
                                           @Param("status") String status,
                                           Pageable pageable);

    @Query(value = "SELECT DISTINCT p.* FROM products p " +
           "JOIN product_categories pc ON p.id = pc.product_id " +
           "JOIN categories c ON pc.category_id = c.id " +
           "WHERE c.slug = :categorySlug AND p.status = CAST(:status AS product_status)",
           nativeQuery = true)
    Page<Product> findByCategorySlugAndStatus(@Param("categorySlug") String categorySlug,
                                            @Param("status") String status,
                                            Pageable pageable);

    // Search queries
    @Query(value = "SELECT p.* FROM products p WHERE " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.brand) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.search_keywords) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "p.status = CAST(:status AS product_status)",
           nativeQuery = true)
    Page<Product> searchByQuery(@Param("query") String query,
                               @Param("status") String status,
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
    @Query(value = "SELECT DISTINCT p.* FROM products p " +
           "JOIN product_categories pc ON p.id = pc.product_id " +
           "WHERE p.is_featured = true AND pc.category_id = :categoryId AND p.status = CAST(:status AS product_status)",
           nativeQuery = true)
    List<Product> findFeaturedByCategoryId(@Param("categoryId") Long categoryId,
                                         @Param("status") String status);

    // Products with inventory
    @Query(value = "SELECT p.* FROM products p " +
           "JOIN inventory i ON p.id = i.product_id " +
           "WHERE i.quantity > 0 AND p.status = CAST(:status AS product_status)",
           nativeQuery = true)
    Page<Product> findInStockProducts(@Param("status") String status, Pageable pageable);

    @Query(value = "SELECT p.* FROM products p " +
           "JOIN inventory i ON p.id = i.product_id " +
           "WHERE i.quantity <= i.min_stock_level AND p.status = CAST(:status AS product_status)",
           nativeQuery = true)
    List<Product> findLowStockProducts(@Param("status") String status);

    // Products on sale
    @Query(value = "SELECT p.* FROM products p WHERE p.compare_price > p.price AND p.status = CAST(:status AS product_status)",
           nativeQuery = true)
    Page<Product> findProductsOnSale(@Param("status") String status, Pageable pageable);

    // Recently added products
    @Query(value = "SELECT p.* FROM products p WHERE p.status = CAST(:status AS product_status) ORDER BY p.created_at DESC",
           nativeQuery = true)
    Page<Product> findRecentProducts(@Param("status") String status, Pageable pageable);

    // Products by multiple brands
    @Query(value = "SELECT p.* FROM products p WHERE p.brand IN :brands AND p.status = CAST(:status AS product_status)",
           nativeQuery = true)
    Page<Product> findByBrandInAndStatus(@Param("brands") List<String> brands, @Param("status") String status, Pageable pageable);

    // Count queries
    long countByStatus(ProductStatus status);

    long countByIsFeaturedTrue();

    long countByBrand(String brand);

    @Query(value = "SELECT COUNT(DISTINCT p.id) FROM products p " +
           "JOIN product_categories pc ON p.id = pc.product_id " +
           "WHERE pc.category_id = :categoryId AND p.status = CAST(:status AS product_status)",
           nativeQuery = true)
    long countByCategoryIdAndStatus(@Param("categoryId") Long categoryId, @Param("status") String status);

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
    @Query(value = "SELECT DISTINCT p2.* FROM products p1 " +
           "JOIN product_categories pc1 ON p1.id = pc1.product_id " +
           "JOIN categories c ON pc1.category_id = c.id " +
           "JOIN product_categories pc2 ON c.id = pc2.category_id " +
           "JOIN products p2 ON pc2.product_id = p2.id " +
           "WHERE p1.id = :productId AND p2.id != :productId AND p2.status = CAST(:status AS product_status) " +
           "ORDER BY p2.created_at DESC",
           nativeQuery = true)
    Page<Product> findRelatedProducts(@Param("productId") Long productId,
                                    @Param("status") String status,
                                    Pageable pageable);
}
