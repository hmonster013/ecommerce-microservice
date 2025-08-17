package org.de013.userservice.repository.specification;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.de013.userservice.entity.Role;
import org.de013.userservice.entity.User;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class UserSpecification {

    /**
     * Search users by keyword (username, email, firstName, lastName)
     */
    public static Specification<User> hasKeyword(String keyword) {
        return (root, query, criteriaBuilder) -> {
            if (keyword == null || keyword.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            
            String likePattern = "%" + keyword.toLowerCase() + "%";
            return criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(root.get("username")), likePattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), likePattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("firstName")), likePattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("lastName")), likePattern)
            );
        };
    }

    /**
     * Filter by enabled status
     */
    public static Specification<User> hasEnabled(Boolean enabled) {
        return (root, query, criteriaBuilder) -> {
            if (enabled == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("enabled"), enabled);
        };
    }

    /**
     * Filter by role name
     */
    public static Specification<User> hasRole(String roleName) {
        return (root, query, criteriaBuilder) -> {
            if (roleName == null || roleName.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            
            Join<User, Role> roleJoin = root.join("roles", JoinType.INNER);
            return criteriaBuilder.equal(criteriaBuilder.lower(roleJoin.get("name")), roleName.toLowerCase());
        };
    }

    /**
     * Filter by creation date range
     */
    public static Specification<User> createdBetween(LocalDateTime startDate, LocalDateTime endDate) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            
            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Filter active users (enabled and account not locked)
     */
    public static Specification<User> isActive() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
            criteriaBuilder.equal(root.get("enabled"), true),
            criteriaBuilder.equal(root.get("accountNonLocked"), true)
        );
    }

    /**
     * Complex filter combining multiple criteria
     */
    public static Specification<User> withFilters(String keyword, Boolean enabled, String roleName, 
                                                 LocalDateTime startDate, LocalDateTime endDate) {
        return Specification.where(hasKeyword(keyword))
                .and(hasEnabled(enabled))
                .and(hasRole(roleName))
                .and(createdBetween(startDate, endDate));
    }
}
