package org.de013.productcatalog.repository.specification;

import jakarta.persistence.criteria.*;
import org.de013.productcatalog.dto.search.ProductSearchDto;
import org.de013.productcatalog.entity.Product;
import org.de013.productcatalog.entity.ProductCategory;
import org.de013.productcatalog.entity.Category;
import org.de013.productcatalog.entity.Inventory;
import org.de013.productcatalog.entity.Review;
import org.de013.productcatalog.entity.enums.ProductStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProductSpecification {

    public static Specification<Product> hasStatus(ProductStatus status) {
        return (root, query, criteriaBuilder) -> {
            if (status == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("status"), status);
        };
    }

    public static Specification<Product> hasStatuses(List<ProductStatus> statuses) {
        return (root, query, criteriaBuilder) -> {
            if (statuses == null || statuses.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return root.get("status").in(statuses);
        };
    }

    public static Specification<Product> hasName(String name) {
        return (root, query, criteriaBuilder) -> {
            if (name == null || name.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(
                criteriaBuilder.lower(root.get("name")), 
                "%" + name.toLowerCase() + "%"
            );
        };
    }

    public static Specification<Product> hasBrand(String brand) {
        return (root, query, criteriaBuilder) -> {
            if (brand == null || brand.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(
                criteriaBuilder.lower(root.get("brand")), 
                brand.toLowerCase()
            );
        };
    }

    public static Specification<Product> hasBrands(List<String> brands) {
        return (root, query, criteriaBuilder) -> {
            if (brands == null || brands.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            List<String> lowerCaseBrands = brands.stream()
                .map(String::toLowerCase)
                .toList();
            return criteriaBuilder.lower(root.get("brand")).in(lowerCaseBrands);
        };
    }

    public static Specification<Product> hasPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (minPrice != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            
            if (maxPrice != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Product> isFeatured(Boolean featured) {
        return (root, query, criteriaBuilder) -> {
            if (featured == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("isFeatured"), featured);
        };
    }

    public static Specification<Product> isDigital(Boolean digital) {
        return (root, query, criteriaBuilder) -> {
            if (digital == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("isDigital"), digital);
        };
    }

    public static Specification<Product> hasCategories(List<Long> categoryIds) {
        return (root, query, criteriaBuilder) -> {
            if (categoryIds == null || categoryIds.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            
            Join<Product, ProductCategory> productCategoryJoin = root.join("productCategories");
            Join<ProductCategory, Category> categoryJoin = productCategoryJoin.join("category");
            
            return categoryJoin.get("id").in(categoryIds);
        };
    }

    public static Specification<Product> isInStock(Boolean inStock) {
        return (root, query, criteriaBuilder) -> {
            if (inStock == null || !inStock) {
                return criteriaBuilder.conjunction();
            }
            
            Join<Product, Inventory> inventoryJoin = root.join("inventory", JoinType.LEFT);
            return criteriaBuilder.greaterThan(
                criteriaBuilder.diff(inventoryJoin.get("quantity"), inventoryJoin.get("reservedQuantity")), 
                0
            );
        };
    }

    public static Specification<Product> isOnSale(Boolean onSale) {
        return (root, query, criteriaBuilder) -> {
            if (onSale == null || !onSale) {
                return criteriaBuilder.conjunction();
            }
            
            return criteriaBuilder.and(
                criteriaBuilder.isNotNull(root.get("comparePrice")),
                criteriaBuilder.greaterThan(root.get("comparePrice"), root.get("price"))
            );
        };
    }

    public static Specification<Product> hasMinRating(Double minRating) {
        return (root, query, criteriaBuilder) -> {
            if (minRating == null) {
                return criteriaBuilder.conjunction();
            }
            
            Subquery<Double> avgRatingSubquery = query.subquery(Double.class);
            Root<Review> reviewRoot = avgRatingSubquery.from(Review.class);
            
            avgRatingSubquery.select(criteriaBuilder.avg(reviewRoot.get("rating")))
                .where(
                    criteriaBuilder.equal(reviewRoot.get("product"), root),
                    criteriaBuilder.equal(reviewRoot.get("status"), org.de013.productcatalog.entity.enums.ReviewStatus.APPROVED)
                );
            
            return criteriaBuilder.greaterThanOrEqualTo(avgRatingSubquery, minRating);
        };
    }

    public static Specification<Product> searchInFields(String query, ProductSearchDto searchDto) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            if (query == null || query.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            
            String searchTerm = "%" + query.toLowerCase() + "%";
            List<Predicate> searchPredicates = new ArrayList<>();
            
            // Search in name
            if (searchDto.getSearchInName() != null && searchDto.getSearchInName()) {
                searchPredicates.add(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), searchTerm)
                );
            }
            
            // Search in description
            if (searchDto.getSearchInDescription() != null && searchDto.getSearchInDescription()) {
                searchPredicates.add(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), searchTerm)
                );
            }
            
            // Search in SKU
            if (searchDto.getSearchInSku() != null && searchDto.getSearchInSku()) {
                searchPredicates.add(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("sku")), searchTerm)
                );
            }
            
            // Search in brand
            if (searchDto.getSearchInBrand() != null && searchDto.getSearchInBrand()) {
                searchPredicates.add(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("brand")), searchTerm)
                );
            }
            
            // Search in keywords
            if (searchDto.getSearchInKeywords() != null && searchDto.getSearchInKeywords()) {
                searchPredicates.add(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("searchKeywords")), searchTerm)
                );
            }
            
            return criteriaBuilder.or(searchPredicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Product> buildFromSearchDto(ProductSearchDto searchDto) {
        return Specification.where(hasStatuses(searchDto.getStatuses()))
                .and(hasBrands(searchDto.getBrands()))
                .and(hasPriceRange(searchDto.getMinPrice(), searchDto.getMaxPrice()))
                .and(hasCategories(searchDto.getCategoryIds()))
                .and(isFeatured(searchDto.getFeaturedOnly()))
                .and(isDigital(searchDto.getDigitalOnly()))
                .and(isInStock(searchDto.getInStockOnly()))
                .and(isOnSale(searchDto.getOnSaleOnly()))
                .and(hasMinRating(searchDto.getMinRating()))
                .and(searchInFields(searchDto.getQuery(), searchDto));
    }

    // Helper method for distinct results when joining
    public static Specification<Product> distinct() {
        return (root, query, criteriaBuilder) -> {
            query.distinct(true);
            return criteriaBuilder.conjunction();
        };
    }
}
