package org.de013.userservice.repository;

import org.de013.userservice.entity.Permission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {
    
    // ========== Basic Finder Methods ==========
    
    Optional<Permission> findByName(String name);
    
    List<Permission> findByNameIn(List<String> names);
    
    // ========== Existence Check Methods ==========
    
    boolean existsByName(String name);
    
    boolean existsByNameAndIdNot(String name, Long id);
    
    // ========== Status-based Queries ==========
    
    List<Permission> findByActive(boolean active);
    
    @Query("SELECT p FROM Permission p WHERE p.active = true")
    List<Permission> findAllActive();
    
    @Query("SELECT p FROM Permission p WHERE p.active = true ORDER BY p.resource, p.action")
    List<Permission> findAllActiveOrderByResourceAndAction();
    
    Page<Permission> findByActive(boolean active, Pageable pageable);
    
    // ========== Resource-based Queries ==========
    
    List<Permission> findByResource(String resource);
    
    List<Permission> findByResourceAndActive(String resource, boolean active);
    
    List<Permission> findByAction(String action);
    
    List<Permission> findByActionAndActive(String action, boolean active);
    
    List<Permission> findByResourceAndAction(String resource, String action);
    
    @Query("SELECT DISTINCT p.resource FROM Permission p WHERE p.active = true ORDER BY p.resource")
    List<String> findAllActiveResources();
    
    @Query("SELECT DISTINCT p.action FROM Permission p WHERE p.active = true ORDER BY p.action")
    List<String> findAllActiveActions();
    
    // ========== Search and Filtering ==========
    
    @Query("SELECT p FROM Permission p WHERE " +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.resource) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.action) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Permission> searchPermissions(@Param("keyword") String keyword, Pageable pageable);
    
    @Query("SELECT p FROM Permission p WHERE " +
           "(LOWER(p.resource) LIKE LOWER(CONCAT('%', :resource, '%')) OR :resource IS NULL) AND " +
           "(LOWER(p.action) LIKE LOWER(CONCAT('%', :action, '%')) OR :action IS NULL) AND " +
           "(p.active = :active OR :active IS NULL)")
    Page<Permission> findPermissionsWithFilters(@Param("resource") String resource,
                                               @Param("action") String action,
                                               @Param("active") Boolean active,
                                               Pageable pageable);
    
    // ========== Statistics Queries ==========
    
    @Query("SELECT COUNT(p) FROM Permission p WHERE p.active = true")
    long countActivePermissions();
    
    @Query("SELECT p.resource, COUNT(p) FROM Permission p WHERE p.active = true GROUP BY p.resource")
    List<Object[]> getPermissionCountByResource();
    
    @Query("SELECT p.action, COUNT(p) FROM Permission p WHERE p.active = true GROUP BY p.action")
    List<Object[]> getPermissionCountByAction();
    
    // ========== Role-related Queries ==========
    
    @Query("SELECT COUNT(DISTINCT r) FROM Role r JOIN r.permissions p WHERE p.id = :permissionId")
    long countRolesByPermissionId(@Param("permissionId") Long permissionId);
    
    @Query("SELECT p FROM Permission p WHERE p NOT IN " +
           "(SELECT perm FROM Role r JOIN r.permissions perm WHERE r.id = :roleId)")
    List<Permission> findPermissionsNotInRole(@Param("roleId") Long roleId);
}
