package org.de013.productcatalog.repository;

import org.de013.productcatalog.entity.Category;
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
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // Basic queries
    Optional<Category> findBySlug(String slug);
    
    List<Category> findByIsActiveTrue();
    
    Page<Category> findByIsActiveTrue(Pageable pageable);
    
    List<Category> findByIsActiveTrueOrderByDisplayOrderAsc();

    // Hierarchy queries
    List<Category> findByParentIsNull();
    
    List<Category> findByParentIsNullAndIsActiveTrue();
    
    List<Category> findByParentIsNullAndIsActiveTrueOrderByDisplayOrderAsc();
    
    List<Category> findByParentId(Long parentId);
    
    List<Category> findByParentIdAndIsActiveTrue(Long parentId);
    
    List<Category> findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(Long parentId);
    
    Page<Category> findByParentId(Long parentId, Pageable pageable);

    // Level-based queries
    List<Category> findByLevel(Integer level);
    
    List<Category> findByLevelAndIsActiveTrue(Integer level);
    
    List<Category> findByLevelAndIsActiveTrueOrderByDisplayOrderAsc(Integer level);

    // Name-based queries
    List<Category> findByNameContainingIgnoreCase(String name);
    
    Page<Category> findByNameContainingIgnoreCase(String name, Pageable pageable);
    
    List<Category> findByNameContainingIgnoreCaseAndIsActiveTrue(String name);

    // Hierarchy tree queries
    @Query("SELECT c FROM Category c WHERE c.parent IS NULL AND c.isActive = true ORDER BY c.displayOrder ASC")
    List<Category> findRootCategories();

    @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId AND c.isActive = true ORDER BY c.displayOrder ASC")
    List<Category> findChildCategories(@Param("parentId") Long parentId);

    @Query("SELECT c FROM Category c WHERE c.level <= :maxLevel AND c.isActive = true ORDER BY c.level ASC, c.displayOrder ASC")
    List<Category> findCategoriesUpToLevel(@Param("maxLevel") Integer maxLevel);

    // Category path queries
    @Query(value = "WITH RECURSIVE category_path AS (" +
           "  SELECT id, name, slug, parent_id, level, 0 as depth " +
           "  FROM categories WHERE id = :categoryId " +
           "  UNION ALL " +
           "  SELECT c.id, c.name, c.slug, c.parent_id, c.level, cp.depth + 1 " +
           "  FROM categories c " +
           "  INNER JOIN category_path cp ON c.id = cp.parent_id " +
           ") " +
           "SELECT * FROM category_path ORDER BY depth DESC",
           nativeQuery = true)
    List<Object[]> findCategoryPath(@Param("categoryId") Long categoryId);

    // Category with product count
    @Query("SELECT c, COUNT(pc.product) FROM Category c " +
           "LEFT JOIN c.productCategories pc " +
           "LEFT JOIN pc.product p ON p.status = 'ACTIVE' " +
           "WHERE c.isActive = true " +
           "GROUP BY c " +
           "ORDER BY c.displayOrder ASC")
    List<Object[]> findCategoriesWithProductCount();

    @Query("SELECT c, COUNT(pc.product) FROM Category c " +
           "LEFT JOIN c.productCategories pc " +
           "LEFT JOIN pc.product p ON p.status = 'ACTIVE' " +
           "WHERE c.parent IS NULL AND c.isActive = true " +
           "GROUP BY c " +
           "ORDER BY c.displayOrder ASC")
    List<Object[]> findRootCategoriesWithProductCount();

    @Query("SELECT c, COUNT(pc.product) FROM Category c " +
           "LEFT JOIN c.productCategories pc " +
           "LEFT JOIN pc.product p ON p.status = 'ACTIVE' " +
           "WHERE c.parent.id = :parentId AND c.isActive = true " +
           "GROUP BY c " +
           "ORDER BY c.displayOrder ASC")
    List<Object[]> findChildCategoriesWithProductCount(@Param("parentId") Long parentId);

    // Category descendants (all children recursively)
    @Query(value = "WITH RECURSIVE category_descendants AS (" +
                   "  SELECT id, name, slug, parent_id, level " +
                   "  FROM categories WHERE parent_id = :categoryId AND is_active = true " +
                   "  UNION ALL " +
                   "  SELECT c.id, c.name, c.slug, c.parent_id, c.level " +
                   "  FROM categories c " +
                   "  INNER JOIN category_descendants cd ON c.parent_id = cd.id " +
                   "  WHERE c.is_active = true " +
                   ") " +
                   "SELECT * FROM category_descendants ORDER BY level ASC",
           nativeQuery = true)
    List<Object[]> findCategoryDescendants(@Param("categoryId") Long categoryId);

    // Category ancestors (all parents recursively)
    @Query(value = "WITH RECURSIVE category_ancestors AS (" +
                   "  SELECT id, name, slug, parent_id, level " +
                   "  FROM categories WHERE id = :categoryId " +
                   "  UNION ALL " +
                   "  SELECT c.id, c.name, c.slug, c.parent_id, c.level " +
                   "  FROM categories c " +
                   "  INNER JOIN category_ancestors ca ON c.id = ca.parent_id " +
                   ") " +
                   "SELECT * FROM category_ancestors WHERE id != :categoryId ORDER BY level ASC",
           nativeQuery = true)
    List<Object[]> findCategoryAncestors(@Param("categoryId") Long categoryId);

    // Popular categories (by product count)
    @Query("SELECT c FROM Category c " +
           "LEFT JOIN c.productCategories pc " +
           "LEFT JOIN pc.product p ON p.status = 'ACTIVE' " +
           "WHERE c.isActive = true " +
           "GROUP BY c " +
           "HAVING COUNT(pc.product) > 0 " +
           "ORDER BY COUNT(pc.product) DESC")
    Page<Category> findPopularCategories(Pageable pageable);

    // Count queries
    long countByIsActiveTrue();
    
    long countByParentIsNullAndIsActiveTrue();
    
    long countByParentIdAndIsActiveTrue(Long parentId);
    
    @Query("SELECT COUNT(c) FROM Category c WHERE c.level = :level AND c.isActive = true")
    long countByLevelAndIsActiveTrue(@Param("level") Integer level);

    // Exists queries
    boolean existsBySlug(String slug);
    
    boolean existsBySlugAndIdNot(String slug, Long id);
    
    boolean existsByParentId(Long parentId);

    // Bulk operations
    @Modifying
    @Query("UPDATE Category c SET c.isActive = :active WHERE c.parent.id = :parentId")
    int bulkUpdateActiveStatusByParentId(@Param("parentId") Long parentId, @Param("active") boolean active);

    @Modifying
    @Query("UPDATE Category c SET c.level = c.level + :levelIncrement WHERE c.parent.id = :parentId")
    int bulkUpdateLevelByParentId(@Param("parentId") Long parentId, @Param("levelIncrement") Integer levelIncrement);

    // Category breadcrumbs
    @Query("SELECT c FROM Category c WHERE c.id IN :categoryIds ORDER BY c.level ASC")
    List<Category> findCategoriesForBreadcrumbs(@Param("categoryIds") List<Long> categoryIds);

    // Categories by name pattern
    @Query("SELECT c FROM Category c WHERE " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :pattern, '%')) AND " +
           "c.isActive = true " +
           "ORDER BY c.level ASC, c.displayOrder ASC")
    List<Category> findByNamePattern(@Param("pattern") String pattern);

    // Categories with no products
    @Query("SELECT c FROM Category c " +
           "LEFT JOIN c.productCategories pc " +
           "WHERE c.isActive = true " +
           "GROUP BY c " +
           "HAVING COUNT(pc.product) = 0")
    List<Category> findCategoriesWithNoProducts();

    // Max display order for siblings
    @Query("SELECT COALESCE(MAX(c.displayOrder), 0) FROM Category c WHERE " +
           "(:parentId IS NULL AND c.parent IS NULL) OR " +
           "(:parentId IS NOT NULL AND c.parent.id = :parentId)")
    Integer findMaxDisplayOrderBySiblings(@Param("parentId") Long parentId);
}
