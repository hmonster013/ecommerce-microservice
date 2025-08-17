package org.de013.productcatalog.repository;

import org.de013.productcatalog.entity.Review;
import org.de013.productcatalog.entity.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // Basic queries
    List<Review> findByProductId(Long productId);
    
    Page<Review> findByProductId(Long productId, Pageable pageable);
    
    List<Review> findByUserId(Long userId);
    
    Page<Review> findByUserId(Long userId, Pageable pageable);

    // Status-based queries
    List<Review> findByStatus(ReviewStatus status);
    
    Page<Review> findByStatus(ReviewStatus status, Pageable pageable);
    
    List<Review> findByProductIdAndStatus(Long productId, ReviewStatus status);
    
    Page<Review> findByProductIdAndStatus(Long productId, ReviewStatus status, Pageable pageable);
    
    List<Review> findByUserIdAndStatus(Long userId, ReviewStatus status);

    // Approved reviews (public)
    @Query("SELECT r FROM Review r WHERE r.productId = :productId AND r.status = 'APPROVED' ORDER BY r.createdAt DESC")
    List<Review> findApprovedByProductId(@Param("productId") Long productId);

    @Query("SELECT r FROM Review r WHERE r.productId = :productId AND r.status = 'APPROVED' ORDER BY r.createdAt DESC")
    Page<Review> findApprovedByProductId(@Param("productId") Long productId, Pageable pageable);

    // Rating-based queries
    List<Review> findByProductIdAndRating(Long productId, Integer rating);
    
    List<Review> findByProductIdAndRatingAndStatus(Long productId, Integer rating, ReviewStatus status);
    
    List<Review> findByProductIdAndRatingGreaterThanEqual(Long productId, Integer minRating);
    
    List<Review> findByProductIdAndRatingLessThanEqual(Long productId, Integer maxRating);

    @Query("SELECT r FROM Review r WHERE r.productId = :productId AND r.rating BETWEEN :minRating AND :maxRating AND r.status = 'APPROVED'")
    List<Review> findByProductIdAndRatingRange(@Param("productId") Long productId, 
                                              @Param("minRating") Integer minRating, 
                                              @Param("maxRating") Integer maxRating);

    // Verified purchase queries
    List<Review> findByProductIdAndVerifiedPurchaseTrue(Long productId);
    
    List<Review> findByProductIdAndVerifiedPurchaseTrueAndStatus(Long productId, ReviewStatus status);

    // Helpful reviews
    @Query("SELECT r FROM Review r WHERE r.productId = :productId AND r.status = 'APPROVED' ORDER BY r.helpfulCount DESC")
    List<Review> findMostHelpfulByProductId(@Param("productId") Long productId, Pageable pageable);

    @Query("SELECT r FROM Review r WHERE r.productId = :productId AND r.helpfulCount > :minHelpful AND r.status = 'APPROVED'")
    List<Review> findHelpfulByProductId(@Param("productId") Long productId, @Param("minHelpful") Integer minHelpful);

    // Recent reviews
    @Query("SELECT r FROM Review r WHERE r.productId = :productId AND r.status = 'APPROVED' ORDER BY r.createdAt DESC")
    List<Review> findRecentByProductId(@Param("productId") Long productId, Pageable pageable);

    @Query("SELECT r FROM Review r WHERE r.status = 'APPROVED' AND r.createdAt >= :since ORDER BY r.createdAt DESC")
    List<Review> findRecentReviews(@Param("since") LocalDateTime since);

    // Reviews needing moderation
    @Query("SELECT r FROM Review r WHERE r.status IN ('PENDING', 'FLAGGED') ORDER BY r.createdAt ASC")
    List<Review> findReviewsNeedingModeration();

    @Query("SELECT r FROM Review r WHERE r.status IN ('PENDING', 'FLAGGED') ORDER BY r.createdAt ASC")
    Page<Review> findReviewsNeedingModeration(Pageable pageable);

    // Search reviews
    @Query("SELECT r FROM Review r WHERE r.productId = :productId AND " +
           "(LOWER(r.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(r.comment) LIKE LOWER(CONCAT('%', :query, '%'))) AND r.status = 'APPROVED'")
    List<Review> searchByProductIdAndQuery(@Param("productId") Long productId, @Param("query") String query);

    // Rating aggregation queries
    @Query("SELECT AVG(CAST(r.rating AS double)) FROM Review r WHERE r.productId = :productId AND r.status = 'APPROVED'")
    Optional<Double> findAverageRatingByProductId(@Param("productId") Long productId);

    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.productId = :productId AND r.status = 'APPROVED' GROUP BY r.rating ORDER BY r.rating DESC")
    List<Object[]> findRatingDistributionByProductId(@Param("productId") Long productId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.productId = :productId AND r.status = 'APPROVED'")
    long countApprovedByProductId(@Param("productId") Long productId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.productId = :productId AND r.status = 'APPROVED' AND r.verifiedPurchase = true")
    long countVerifiedByProductId(@Param("productId") Long productId);

    // Review statistics
    @Query("SELECT COUNT(r) FROM Review r WHERE r.productId = :productId AND r.rating >= 4 AND r.status = 'APPROVED'")
    long countPositiveReviewsByProductId(@Param("productId") Long productId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.productId = :productId AND r.rating <= 2 AND r.status = 'APPROVED'")
    long countNegativeReviewsByProductId(@Param("productId") Long productId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.productId = :productId AND r.rating = 3 AND r.status = 'APPROVED'")
    long countNeutralReviewsByProductId(@Param("productId") Long productId);

    // Multiple products rating aggregation
    @Query("SELECT r.productId, AVG(CAST(r.rating AS double)), COUNT(r) FROM Review r " +
           "WHERE r.productId IN :productIds AND r.status = 'APPROVED' " +
           "GROUP BY r.productId")
    List<Object[]> findRatingStatsByProductIds(@Param("productIds") List<Long> productIds);

    // User review history
    @Query("SELECT r FROM Review r WHERE r.userId = :userId AND r.status = 'APPROVED' ORDER BY r.createdAt DESC")
    Page<Review> findUserReviewHistory(@Param("userId") Long userId, Pageable pageable);

    Optional<Review> findByProductIdAndUserId(Long productId, Long userId);

    boolean existsByProductIdAndUserId(Long productId, Long userId);

    // Time-based queries
    @Query("SELECT r FROM Review r WHERE r.productId = :productId AND r.createdAt >= :since AND r.status = 'APPROVED'")
    List<Review> findByProductIdSince(@Param("productId") Long productId, @Param("since") LocalDateTime since);

    @Query("SELECT r FROM Review r WHERE r.createdAt BETWEEN :start AND :end AND r.status = 'APPROVED'")
    List<Review> findByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Count queries by status
    long countByStatus(ReviewStatus status);
    
    long countByProductIdAndStatus(Long productId, ReviewStatus status);
    
    long countByUserIdAndStatus(Long userId, ReviewStatus status);

    // Bulk operations
    @Modifying
    @Query("UPDATE Review r SET r.status = :newStatus WHERE r.status = :oldStatus")
    int bulkUpdateStatus(@Param("oldStatus") ReviewStatus oldStatus, @Param("newStatus") ReviewStatus newStatus);

    @Modifying
    @Query("UPDATE Review r SET r.status = :status, r.moderatedBy = :moderatorId, r.moderatedAt = :moderatedAt " +
           "WHERE r.id IN :reviewIds")
    int bulkModerateReviews(@Param("reviewIds") List<Long> reviewIds, 
                           @Param("status") ReviewStatus status, 
                           @Param("moderatorId") String moderatorId, 
                           @Param("moderatedAt") LocalDateTime moderatedAt);

    @Modifying
    @Query("UPDATE Review r SET r.helpfulCount = r.helpfulCount + 1 WHERE r.id = :reviewId")
    int incrementHelpfulCount(@Param("reviewId") Long reviewId);

    @Modifying
    @Query("UPDATE Review r SET r.notHelpfulCount = r.notHelpfulCount + 1 WHERE r.id = :reviewId")
    int incrementNotHelpfulCount(@Param("reviewId") Long reviewId);

    // Top reviewers
    @Query("SELECT r.userId, r.reviewerName, COUNT(r) as review_count, AVG(CAST(r.rating AS double)) as avg_rating " +
           "FROM Review r WHERE r.status = 'APPROVED' " +
           "GROUP BY r.userId, r.reviewerName " +
           "HAVING COUNT(r) >= :minReviews " +
           "ORDER BY review_count DESC")
    Page<Object[]> findTopReviewers(@Param("minReviews") Integer minReviews, Pageable pageable);

    // Product review summary
    @Query("SELECT r.productId, COUNT(r) as total_reviews, AVG(CAST(r.rating AS double)) as avg_rating, " +
           "SUM(CASE WHEN r.verifiedPurchase = true THEN 1 ELSE 0 END) as verified_reviews " +
           "FROM Review r WHERE r.productId IN :productIds AND r.status = 'APPROVED' " +
           "GROUP BY r.productId")
    List<Object[]> findReviewSummaryByProductIds(@Param("productIds") List<Long> productIds);

    // Reviews by rating for multiple products
    @Query("SELECT r.productId, r.rating, COUNT(r) FROM Review r " +
           "WHERE r.productId IN :productIds AND r.status = 'APPROVED' " +
           "GROUP BY r.productId, r.rating " +
           "ORDER BY r.productId, r.rating DESC")
    List<Object[]> findRatingDistributionByProductIds(@Param("productIds") List<Long> productIds);
}
