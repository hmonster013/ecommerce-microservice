package org.de013.userservice.repository;

import org.de013.userservice.entity.Permission;
import org.de013.userservice.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    // ========== Basic Finder Methods ==========

    Optional<Role> findByName(String name);

    List<Role> findByNameIn(List<String> names);

    // ========== Existence Check Methods ==========

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    // ========== Status-based Queries ==========

    List<Role> findByActive(boolean active);

    @Query("SELECT r FROM Role r WHERE r.active = true")
    List<Role> findAllActive();

    @Query("SELECT r FROM Role r WHERE r.active = true ORDER BY r.name")
    List<Role> findAllActiveOrderByName();

    Page<Role> findByActive(boolean active, Pageable pageable);

    // ========== Permission-based Queries ==========

    @Query("SELECT r FROM Role r JOIN r.permissions p WHERE p.name = :permissionName")
    List<Role> findByPermissionName(@Param("permissionName") String permissionName);

    @Query("SELECT r FROM Role r JOIN r.permissions p WHERE p IN :permissions")
    List<Role> findByPermissionsIn(@Param("permissions") Set<Permission> permissions);

    @Query("SELECT r FROM Role r WHERE SIZE(r.permissions) > :minPermissions")
    List<Role> findRolesWithMinimumPermissions(@Param("minPermissions") int minPermissions);

    // ========== Search and Filtering ==========

    @Query("SELECT r FROM Role r WHERE " +
           "LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(r.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Role> searchRoles(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT r FROM Role r WHERE " +
           "(LOWER(r.name) LIKE LOWER(CONCAT('%', :name, '%')) OR :name IS NULL) AND " +
           "(LOWER(r.description) LIKE LOWER(CONCAT('%', :description, '%')) OR :description IS NULL) AND " +
           "(r.active = :active OR :active IS NULL)")
    Page<Role> findRolesWithFilters(@Param("name") String name,
                                   @Param("description") String description,
                                   @Param("active") Boolean active,
                                   Pageable pageable);

    // ========== Statistics Queries ==========

    @Query("SELECT COUNT(r) FROM Role r WHERE r.active = true")
    long countActiveRoles();

    @Query("SELECT COUNT(DISTINCT u) FROM User u JOIN u.roles r WHERE r.id = :roleId")
    long countUsersByRoleId(@Param("roleId") Long roleId);

    @Query("SELECT r.name, COUNT(u) FROM Role r LEFT JOIN User u ON r MEMBER OF u.roles GROUP BY r.id, r.name")
    List<Object[]> getRoleUsageStatistics();

    // ========== Default Roles ==========

    @Query("SELECT r FROM Role r WHERE r.name IN ('ADMIN', 'CUSTOMER', 'MANAGER', 'SUPPORT') AND r.active = true")
    List<Role> findDefaultRoles();

    @Query("SELECT r FROM Role r WHERE r.name = 'CUSTOMER' AND r.active = true")
    Optional<Role> findDefaultCustomerRole();
}
