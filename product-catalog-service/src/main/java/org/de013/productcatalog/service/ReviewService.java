package org.de013.productcatalog.service;

import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.review.ReviewCreateDto;
import org.de013.productcatalog.dto.review.ReviewResponseDto;
import org.de013.productcatalog.dto.review.ReviewSummaryDto;
import org.de013.productcatalog.dto.review.ReviewUpdateDto;
import org.de013.productcatalog.entity.enums.ReviewStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReviewService {

    // CRUD Operations
    ReviewResponseDto createReview(ReviewCreateDto createDto);
    
    ReviewResponseDto updateReview(Long id, ReviewUpdateDto updateDto);
    
    void deleteReview(Long id);
    
    ReviewResponseDto getReviewById(Long id);

    // Review Listing
    PageResponse<ReviewResponseDto> getAllReviews(Pageable pageable);
    
    PageResponse<ReviewResponseDto> getReviewsByProductId(Long productId, Pageable pageable);
    
    PageResponse<ReviewResponseDto> getReviewsByUserId(Long userId, Pageable pageable);
    
    PageResponse<ReviewResponseDto> getReviewsByStatus(ReviewStatus status, Pageable pageable);

    // Public Reviews (Approved)
    PageResponse<ReviewResponseDto> getApprovedReviewsByProductId(Long productId, Pageable pageable);
    
    List<ReviewResponseDto> getRecentReviewsByProductId(Long productId, int limit);
    
    List<ReviewResponseDto> getMostHelpfulReviewsByProductId(Long productId, int limit);

    // Review Moderation
    PageResponse<ReviewResponseDto> getReviewsNeedingModeration(Pageable pageable);
    
    ReviewResponseDto approveReview(Long id, String moderatorId);
    
    ReviewResponseDto rejectReview(Long id, String moderatorId, String reason);
    
    ReviewResponseDto flagReview(Long id, String moderatorId, String reason);
    
    List<ReviewResponseDto> bulkModerateReviews(List<Long> reviewIds, ReviewStatus status, String moderatorId);

    // Review Statistics & Aggregation
    ReviewSummaryDto getReviewSummaryByProductId(Long productId);
    
    List<Object[]> getRatingDistributionByProductId(Long productId);
    
    Optional<Double> getAverageRatingByProductId(Long productId);
    
    long getReviewCountByProductId(Long productId);
    
    long getVerifiedReviewCountByProductId(Long productId);

    // Review Filtering
    PageResponse<ReviewResponseDto> getReviewsByProductIdAndRating(Long productId, Integer rating, Pageable pageable);
    
    PageResponse<ReviewResponseDto> getReviewsByProductIdAndRatingRange(Long productId, Integer minRating, Integer maxRating, Pageable pageable);
    
    PageResponse<ReviewResponseDto> getVerifiedReviewsByProductId(Long productId, Pageable pageable);
    
    List<ReviewResponseDto> getReviewsByProductIdSince(Long productId, LocalDateTime since);

    // Review Search
    PageResponse<ReviewResponseDto> searchReviewsByProductId(Long productId, String query, Pageable pageable);
    
    PageResponse<ReviewResponseDto> searchReviews(String query, Pageable pageable);

    // Review Helpfulness
    ReviewResponseDto markReviewHelpful(Long reviewId);
    
    ReviewResponseDto markReviewNotHelpful(Long reviewId);
    
    List<ReviewResponseDto> getHelpfulReviewsByProductId(Long productId, Integer minHelpfulCount);

    // User Review Operations
    Optional<ReviewResponseDto> getUserReviewForProduct(Long productId, Long userId);
    
    boolean hasUserReviewedProduct(Long productId, Long userId);
    
    PageResponse<ReviewResponseDto> getUserReviewHistory(Long userId, Pageable pageable);

    // Review Validation
    void validateReviewData(ReviewCreateDto createDto);
    
    void validateReviewData(ReviewUpdateDto updateDto, Long reviewId);
    
    boolean canUserReviewProduct(Long productId, Long userId);

    // Review Statistics
    long getTotalReviewCount();
    
    long getReviewCountByStatus(ReviewStatus status);
    
    long getPendingReviewCount();
    
    long getApprovedReviewCount();
    
    long getRejectedReviewCount();

    // Review Analytics
    List<Object[]> getReviewStatsByProductIds(List<Long> productIds);
    
    List<Object[]> getRatingDistributionByProductIds(List<Long> productIds);
    
    PageResponse<Object[]> getTopReviewers(Integer minReviews, Pageable pageable);

    // Time-based Queries
    List<ReviewResponseDto> getRecentReviews(int limit);
    
    List<ReviewResponseDto> getReviewsByDateRange(LocalDateTime start, LocalDateTime end);
    
    PageResponse<ReviewResponseDto> getRecentApprovedReviews(Pageable pageable);

    // Bulk Operations
    List<ReviewResponseDto> getReviewsByIds(List<Long> reviewIds);
    
    void bulkDeleteReviews(List<Long> reviewIds);
    
    List<ReviewResponseDto> bulkUpdateReviewStatus(List<Long> reviewIds, ReviewStatus status);

    // Review Reporting
    List<ReviewResponseDto> getReviewsWithHighHelpfulnessRatio(Double minRatio);
    
    List<ReviewResponseDto> getReviewsWithLowHelpfulnessRatio(Double maxRatio);
    
    List<ReviewResponseDto> getSuspiciousReviews();

    // Cache Operations
    void clearReviewCache();
    
    void clearReviewCache(Long reviewId);
    
    void clearProductReviewCache(Long productId);
    
    void refreshReviewCache(Long reviewId);
}
