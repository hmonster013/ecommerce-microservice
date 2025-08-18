package org.de013.productcatalog.repository.specification;

import jakarta.persistence.criteria.*;
import lombok.extern.slf4j.Slf4j;
import org.de013.productcatalog.dto.search.ProductSearchDto;
import org.de013.productcatalog.entity.*;
import org.de013.productcatalog.entity.enums.ProductStatus;
import org.de013.productcatalog.util.SearchUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Advanced JPA Specifications for dynamic product queries.
 * Provides comprehensive search and filtering capabilities.
 */
@Slf4j
public class AdvancedProductSpecification {

    /**
     * Create specification from search DTO
     */
    public static Specification<Product> fromSearchDto(ProductSearchDto searchDto) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Text search
            if (StringUtils.hasText(searchDto.getQuery())) {
                predicates.add(createTextSearchPredicate(searchDto.getQuery(), root, criteriaBuilder));
            }

            // Category filtering
            if (searchDto.getCategoryIds() != null && !searchDto.getCategoryIds().isEmpty()) {
                predicates.add(createCategoryPredicate(searchDto.getCategoryIds(), root, criteriaBuilder));
            }

            // Price range filtering
            if (searchDto.getMinPrice() != null || searchDto.getMaxPrice() != null) {
                predicates.add(createPriceRangePredicate(searchDto.getMinPrice(), searchDto.getMaxPrice(), root, criteriaBuilder));
            }

            // Brand filtering
            if (searchDto.getBrands() != null && !searchDto.getBrands().isEmpty()) {
                predicates.add(createBrandPredicate(searchDto.getBrands(), root, criteriaBuilder));
            }

            // Rating filtering
            if (searchDto.getMinRating() != null) {
                predicates.add(createRatingPredicate(searchDto.getMinRating(), root, criteriaBuilder));
            }

            // Status filtering (default to ACTIVE only)
            List<ProductStatus> statuses = searchDto.getStatuses();
            if (statuses == null || statuses.isEmpty()) {
                statuses = List.of(ProductStatus.ACTIVE);
            }
            predicates.add(createStatusPredicate(statuses, root, criteriaBuilder));

            // Availability filtering
            if (searchDto.getInStock() != null && searchDto.getInStock()) {
                predicates.add(createInStockPredicate(root, criteriaBuilder));
            }

            // Date range filtering
            if (searchDto.getCreatedAfter() != null || searchDto.getCreatedBefore() != null) {
                predicates.add(createDateRangePredicate(searchDto.getCreatedAfter(), searchDto.getCreatedBefore(), root, criteriaBuilder));
            }

            // Featured products
            if (searchDto.getFeatured() != null && searchDto.getFeatured()) {
                predicates.add(createFeaturedPredicate(root, criteriaBuilder));
            }

            // On sale products
            if (searchDto.getOnSale() != null && searchDto.getOnSale()) {
                predicates.add(createOnSalePredicate(root, criteriaBuilder));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Full-text search predicate
     */
    private static Predicate createTextSearchPredicate(String query, Root<Product> root, CriteriaBuilder cb) {
        String normalizedQuery = SearchUtils.normalizeQuery(query);
        List<String> searchTerms = SearchUtils.extractSearchTerms(query);

        if (searchTerms.isEmpty()) {
            return cb.conjunction();
        }

        List<Predicate> searchPredicates = new ArrayList<>();

        // Check if query looks like a product code
        if (SearchUtils.isProductCode(query)) {
            // Exact SKU match gets highest priority
            searchPredicates.add(cb.equal(cb.upper(root.get("sku")), query.toUpperCase()));
        }

        // Search in product name (highest weight)
        for (String term : searchTerms) {
            searchPredicates.add(cb.like(cb.lower(root.get("name")), "%" + term.toLowerCase() + "%"));
        }

        // Search in description
        for (String term : searchTerms) {
            searchPredicates.add(cb.like(cb.lower(root.get("description")), "%" + term.toLowerCase() + "%"));
        }

        // Search in short description
        for (String term : searchTerms) {
            searchPredicates.add(cb.like(cb.lower(root.get("shortDescription")), "%" + term.toLowerCase() + "%"));
        }

        // Search in brand
        for (String term : searchTerms) {
            searchPredicates.add(cb.like(cb.lower(root.get("brand")), "%" + term.toLowerCase() + "%"));
        }

        // Search in category names
        Join<Product, ProductCategory> productCategoryJoin = root.join("productCategories", JoinType.LEFT);
        Join<ProductCategory, Category> categoryJoin = productCategoryJoin.join("category", JoinType.LEFT);
        
        for (String term : searchTerms) {
            searchPredicates.add(cb.like(cb.lower(categoryJoin.get("name")), "%" + term.toLowerCase() + "%"));
        }

        return cb.or(searchPredicates.toArray(new Predicate[0]));
    }

    /**
     * Category filtering predicate
     */
    private static Predicate createCategoryPredicate(List<Long> categoryIds, Root<Product> root, CriteriaBuilder cb) {
        Join<Product, ProductCategory> productCategoryJoin = root.join("productCategories", JoinType.INNER);
        Join<ProductCategory, Category> categoryJoin = productCategoryJoin.join("category", JoinType.INNER);
        
        return categoryJoin.get("id").in(categoryIds);
    }

    /**
     * Price range filtering predicate
     */
    private static Predicate createPriceRangePredicate(BigDecimal minPrice, BigDecimal maxPrice, Root<Product> root, CriteriaBuilder cb) {
        List<Predicate> pricePredicates = new ArrayList<>();

        if (minPrice != null) {
            pricePredicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
        }

        if (maxPrice != null) {
            pricePredicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
        }

        return cb.and(pricePredicates.toArray(new Predicate[0]));
    }

    /**
     * Brand filtering predicate
     */
    private static Predicate createBrandPredicate(List<String> brands, Root<Product> root, CriteriaBuilder cb) {
        List<Predicate> brandPredicates = new ArrayList<>();
        
        for (String brand : brands) {
            brandPredicates.add(cb.like(cb.lower(root.get("brand")), "%" + brand.toLowerCase() + "%"));
        }
        
        return cb.or(brandPredicates.toArray(new Predicate[0]));
    }

    /**
     * Rating filtering predicate
     */
    private static Predicate createRatingPredicate(Double minRating, Root<Product> root, CriteriaBuilder cb) {
        // This would require a subquery to calculate average rating from reviews
        Subquery<Double> ratingSubquery = cb.createQuery().subquery(Double.class);
        Root<Review> reviewRoot = ratingSubquery.from(Review.class);
        
        ratingSubquery.select(cb.avg(reviewRoot.get("rating")))
                     .where(cb.equal(reviewRoot.get("product"), root),
                            cb.equal(reviewRoot.get("status"), "APPROVED"));
        
        return cb.greaterThanOrEqualTo(ratingSubquery, minRating);
    }

    /**
     * Status filtering predicate
     */
    private static Predicate createStatusPredicate(List<ProductStatus> statuses, Root<Product> root, CriteriaBuilder cb) {
        return root.get("status").in(statuses);
    }

    /**
     * In stock filtering predicate
     */
    private static Predicate createInStockPredicate(Root<Product> root, CriteriaBuilder cb) {
        Join<Product, Inventory> inventoryJoin = root.join("inventory", JoinType.LEFT);
        return cb.greaterThan(inventoryJoin.get("availableQuantity"), 0);
    }

    /**
     * Date range filtering predicate
     */
    private static Predicate createDateRangePredicate(LocalDateTime createdAfter, LocalDateTime createdBefore, 
                                                     Root<Product> root, CriteriaBuilder cb) {
        List<Predicate> datePredicates = new ArrayList<>();

        if (createdAfter != null) {
            datePredicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdAfter));
        }

        if (createdBefore != null) {
            datePredicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdBefore));
        }

        return cb.and(datePredicates.toArray(new Predicate[0]));
    }

    /**
     * Featured products predicate
     */
    private static Predicate createFeaturedPredicate(Root<Product> root, CriteriaBuilder cb) {
        return cb.isTrue(root.get("featured"));
    }

    /**
     * On sale products predicate
     */
    private static Predicate createOnSalePredicate(Root<Product> root, CriteriaBuilder cb) {
        return cb.and(
            cb.isNotNull(root.get("salePrice")),
            cb.greaterThan(root.get("salePrice"), BigDecimal.ZERO),
            cb.lessThan(root.get("salePrice"), root.get("price"))
        );
    }

    /**
     * Create specification for similar products
     */
    public static Specification<Product> findSimilarProducts(Product product) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Exclude the product itself
            predicates.add(criteriaBuilder.notEqual(root.get("id"), product.getId()));

            // Same category
            if (!product.getProductCategories().isEmpty()) {
                Join<Product, ProductCategory> productCategoryJoin = root.join("productCategories", JoinType.INNER);
                Join<ProductCategory, Category> categoryJoin = productCategoryJoin.join("category", JoinType.INNER);
                
                List<Long> categoryIds = product.getProductCategories().stream()
                    .map(pc -> pc.getCategory().getId())
                    .toList();
                
                predicates.add(categoryJoin.get("id").in(categoryIds));
            }

            // Similar price range (±20%)
            BigDecimal priceVariation = product.getPrice().multiply(BigDecimal.valueOf(0.2));
            BigDecimal minPrice = product.getPrice().subtract(priceVariation);
            BigDecimal maxPrice = product.getPrice().add(priceVariation);
            
            predicates.add(criteriaBuilder.between(root.get("price"), minPrice, maxPrice));

            // Same brand (optional)
            if (StringUtils.hasText(product.getBrand())) {
                predicates.add(criteriaBuilder.equal(
                    criteriaBuilder.lower(root.get("brand")), 
                    product.getBrand().toLowerCase()
                ));
            }

            // Only active products
            predicates.add(criteriaBuilder.equal(root.get("status"), ProductStatus.ACTIVE));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Create specification for trending products
     */
    public static Specification<Product> findTrendingProducts() {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Only active products
            predicates.add(criteriaBuilder.equal(root.get("status"), ProductStatus.ACTIVE));

            // Created in last 30 days OR updated recently
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            Predicate recentlyCreated = criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), thirtyDaysAgo);
            Predicate recentlyUpdated = criteriaBuilder.greaterThanOrEqualTo(root.get("updatedAt"), thirtyDaysAgo);
            
            predicates.add(criteriaBuilder.or(recentlyCreated, recentlyUpdated));

            // Has good rating (would require subquery for actual implementation)
            // For now, we'll use featured products as proxy for trending
            predicates.add(criteriaBuilder.isTrue(root.get("featured")));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
