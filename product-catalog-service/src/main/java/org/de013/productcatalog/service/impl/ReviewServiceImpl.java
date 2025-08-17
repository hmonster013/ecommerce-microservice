package org.de013.productcatalog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.review.ReviewCreateDto;
import org.de013.productcatalog.dto.review.ReviewResponseDto;
import org.de013.productcatalog.dto.review.ReviewSummaryDto;
import org.de013.productcatalog.dto.review.ReviewUpdateDto;
import org.de013.productcatalog.entity.Product;
import org.de013.productcatalog.entity.Review;
import org.de013.productcatalog.entity.enums.ReviewStatus;
import org.de013.productcatalog.repository.ProductRepository;
import org.de013.productcatalog.repository.ReviewRepository;
import org.de013.productcatalog.service.ReviewService;
import org.de013.productcatalog.util.EntityMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final EntityMapper entityMapper;

    @Override
    @Transactional
    public ReviewResponseDto createReview(ReviewCreateDto createDto) {
        log.info("Creating review for product ID: {} by user ID: {}", createDto.getProductId(), createDto.getUserId());
        
        validateReviewData(createDto);
        
        // Check if user already reviewed this product
        if (hasUserReviewedProduct(createDto.getProductId(), createDto.getUserId())) {
            throw new RuntimeException("User has already reviewed this product");
        }
        
        Product product = findProductById(createDto.getProductId());
        
        Review review = Review.builder()
                .product(product)
                .userId(createDto.getUserId())
                .rating(createDto.getRating())
                .title(createDto.getTitle())
                .comment(createDto.getComment())
                .status(ReviewStatus.PENDING)
                .helpfulCount(0)
                .notHelpfulCount(0)
                .verifiedPurchase(createDto.getVerifiedPurchase())
                .reviewerName(createDto.getReviewerName())
                .reviewerEmail(createDto.getReviewerEmail())
                .build();
        
        review = reviewRepository.save(review);
        
        // Clear product review cache
        clearProductReviewCache(createDto.getProductId());
        
        log.info("Review created successfully with ID: {}", review.getId());
        return entityMapper.toReviewResponseDto(review);
    }

    @Override
    @Transactional
    @CacheEvict(value = "reviews", key = "#id")
    public ReviewResponseDto updateReview(Long id, ReviewUpdateDto updateDto) {
        log.info("Updating review with ID: {}", id);
        
        Review review = findReviewById(id);
        validateReviewData(updateDto, id);
        
        updateReviewFields(review, updateDto);
        
        review = reviewRepository.save(review);
        
        // Clear product review cache
        clearProductReviewCache(review.getProduct().getId());
        
        log.info("Review updated successfully with ID: {}", id);
        return entityMapper.toReviewResponseDto(review);
    }

    @Override
    @Transactional
    @CacheEvict(value = "reviews", key = "#id")
    public void deleteReview(Long id) {
        log.info("Deleting review with ID: {}", id);
        
        Review review = findReviewById(id);
        Long productId = review.getProduct().getId();
        
        reviewRepository.delete(review);
        
        // Clear product review cache
        clearProductReviewCache(productId);
        
        log.info("Review deleted successfully with ID: {}", id);
    }

    @Override
    @Cacheable(value = "reviews", key = "#id")
    public ReviewResponseDto getReviewById(Long id) {
        log.debug("Getting review by ID: {}", id);
        
        Review review = findReviewById(id);
        return entityMapper.toReviewResponseDto(review);
    }

    @Override
    public PageResponse<ReviewResponseDto> getAllReviews(Pageable pageable) {
        log.debug("Getting all reviews with pagination: {}", pageable);
        
        Page<Review> reviews = reviewRepository.findAll(pageable);
        return mapToPageResponse(reviews);
    }

    @Override
    @Cacheable(value = "product_reviews", key = "#productId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public PageResponse<ReviewResponseDto> getReviewsByProductId(Long productId, Pageable pageable) {
        log.debug("Getting reviews for product ID: {} with pagination: {}", productId, pageable);
        
        Page<Review> reviews = reviewRepository.findByProductId(productId, pageable);
        return mapToPageResponse(reviews);
    }

    @Override
    public PageResponse<ReviewResponseDto> getReviewsByUserId(Long userId, Pageable pageable) {
        log.debug("Getting reviews for user ID: {} with pagination: {}", userId, pageable);
        
        Page<Review> reviews = reviewRepository.findByUserId(userId, pageable);
        return mapToPageResponse(reviews);
    }

    @Override
    public PageResponse<ReviewResponseDto> getReviewsByStatus(ReviewStatus status, Pageable pageable) {
        log.debug("Getting reviews by status: {} with pagination: {}", status, pageable);
        
        Page<Review> reviews = reviewRepository.findByStatus(status, pageable);
        return mapToPageResponse(reviews);
    }

    @Override
    @Cacheable(value = "approved_reviews", key = "#productId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public PageResponse<ReviewResponseDto> getApprovedReviewsByProductId(Long productId, Pageable pageable) {
        log.debug("Getting approved reviews for product ID: {} with pagination: {}", productId, pageable);
        
        Page<Review> reviews = reviewRepository.findApprovedByProductId(productId, pageable);
        return mapToPageResponse(reviews);
    }

    @Override
    @Cacheable(value = "recent_reviews", key = "#productId + '_' + #limit")
    public List<ReviewResponseDto> getRecentReviewsByProductId(Long productId, int limit) {
        log.debug("Getting {} recent reviews for product ID: {}", limit, productId);
        
        Pageable pageable = PageRequest.of(0, limit, Sort.by("createdAt").descending());
        Page<Review> reviewsPage = reviewRepository.findApprovedByProductId(productId, pageable);
        List<Review> reviews = reviewsPage.getContent();
        
        return reviews.stream()
                .map(entityMapper::toReviewResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "helpful_reviews", key = "#productId + '_' + #limit")
    public List<ReviewResponseDto> getMostHelpfulReviewsByProductId(Long productId, int limit) {
        log.debug("Getting {} most helpful reviews for product ID: {}", limit, productId);
        
        Pageable pageable = PageRequest.of(0, limit);
        List<Review> reviews = reviewRepository.findMostHelpfulByProductId(productId, pageable);
        
        return reviews.stream()
                .map(entityMapper::toReviewResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public PageResponse<ReviewResponseDto> getReviewsNeedingModeration(Pageable pageable) {
        log.debug("Getting reviews needing moderation with pagination: {}", pageable);
        
        Page<Review> reviews = reviewRepository.findReviewsNeedingModeration(pageable);
        return mapToPageResponse(reviews);
    }

    @Override
    @Transactional
    @CacheEvict(value = "reviews", key = "#id")
    public ReviewResponseDto approveReview(Long id, String moderatorId) {
        log.info("Approving review with ID: {} by moderator: {}", id, moderatorId);
        
        Review review = findReviewById(id);
        review.setStatus(ReviewStatus.APPROVED);
        review.setModeratedBy(moderatorId);
        review.setModeratedAt(LocalDateTime.now());
        
        review = reviewRepository.save(review);
        
        // Clear product review cache
        clearProductReviewCache(review.getProduct().getId());
        
        log.info("Review approved successfully with ID: {}", id);
        return entityMapper.toReviewResponseDto(review);
    }

    @Override
    @Transactional
    @CacheEvict(value = "reviews", key = "#id")
    public ReviewResponseDto rejectReview(Long id, String moderatorId, String reason) {
        log.info("Rejecting review with ID: {} by moderator: {} with reason: {}", id, moderatorId, reason);
        
        Review review = findReviewById(id);
        review.setStatus(ReviewStatus.REJECTED);
        review.setModeratedBy(moderatorId);
        review.setModeratedAt(LocalDateTime.now());
        review.setModerationNotes(reason);
        
        review = reviewRepository.save(review);
        
        // Clear product review cache
        clearProductReviewCache(review.getProduct().getId());
        
        log.info("Review rejected successfully with ID: {}", id);
        return entityMapper.toReviewResponseDto(review);
    }

    @Override
    @Transactional
    @CacheEvict(value = "reviews", key = "#id")
    public ReviewResponseDto flagReview(Long id, String moderatorId, String reason) {
        log.info("Flagging review with ID: {} by moderator: {} with reason: {}", id, moderatorId, reason);
        
        Review review = findReviewById(id);
        review.setStatus(ReviewStatus.FLAGGED);
        review.setModeratedBy(moderatorId);
        review.setModeratedAt(LocalDateTime.now());
        review.setModerationNotes(reason);
        
        review = reviewRepository.save(review);
        
        log.info("Review flagged successfully with ID: {}", id);
        return entityMapper.toReviewResponseDto(review);
    }

    @Override
    @Transactional
    public List<ReviewResponseDto> bulkModerateReviews(List<Long> reviewIds, ReviewStatus status, String moderatorId) {
        log.info("Bulk moderating {} reviews to status: {} by moderator: {}", reviewIds.size(), status, moderatorId);
        
        LocalDateTime moderatedAt = LocalDateTime.now();
        reviewRepository.bulkModerateReviews(reviewIds, status, moderatorId, moderatedAt);
        
        // Clear review cache for all affected reviews
        reviewIds.forEach(this::clearReviewCache);
        
        // Get updated reviews
        List<Review> reviews = reviewRepository.findAllById(reviewIds);
        
        log.info("Bulk moderation completed for {} reviews", reviewIds.size());
        return reviews.stream()
                .map(entityMapper::toReviewResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "review_summary", key = "#productId")
    public ReviewSummaryDto getReviewSummaryByProductId(Long productId) {
        log.debug("Getting review summary for product ID: {}", productId);
        
        Optional<Double> avgRating = reviewRepository.findAverageRatingByProductId(productId);
        long totalReviews = reviewRepository.countApprovedByProductId(productId);
        long verifiedReviews = reviewRepository.countVerifiedByProductId(productId);
        List<Object[]> ratingDistribution = reviewRepository.findRatingDistributionByProductId(productId);
        
        // Calculate statistics
        long positiveReviews = reviewRepository.countPositiveReviewsByProductId(productId);
        long negativeReviews = reviewRepository.countNegativeReviewsByProductId(productId);
        long neutralReviews = reviewRepository.countNeutralReviewsByProductId(productId);
        
        double positivePercentage = totalReviews > 0 ? (positiveReviews * 100.0 / totalReviews) : 0.0;
        
        // Build rating distribution
        List<ReviewSummaryDto.RatingCount> ratingCounts = ratingDistribution.stream()
                .map(data -> ReviewSummaryDto.RatingCount.builder()
                        .rating((Integer) data[0])
                        .count(((Long) data[1]).intValue())
                        .percentage(totalReviews > 0 ? ((Long) data[1] * 100.0 / totalReviews) : 0.0)
                        .build())
                .collect(Collectors.toList());
        
        ReviewSummaryDto.ReviewStats stats = ReviewSummaryDto.ReviewStats.builder()
                .positiveReviews((int) positiveReviews)
                .neutralReviews((int) neutralReviews)
                .negativeReviews((int) negativeReviews)
                .positivePercentage(positivePercentage)
                .recommendationPercentage(positivePercentage) // Simplified
                .build();
        
        return ReviewSummaryDto.builder()
                .averageRating(avgRating.orElse(null))
                .totalReviews((int) totalReviews)
                .verifiedReviews((int) verifiedReviews)
                .ratingDistribution(ratingCounts)
                .stats(stats)
                .build();
    }

    @Override
    public List<Object[]> getRatingDistributionByProductId(Long productId) {
        log.debug("Getting rating distribution for product ID: {}", productId);
        return reviewRepository.findRatingDistributionByProductId(productId);
    }

    @Override
    public Optional<Double> getAverageRatingByProductId(Long productId) {
        log.debug("Getting average rating for product ID: {}", productId);
        return reviewRepository.findAverageRatingByProductId(productId);
    }

    @Override
    public long getReviewCountByProductId(Long productId) {
        log.debug("Getting review count for product ID: {}", productId);
        return reviewRepository.countApprovedByProductId(productId);
    }

    @Override
    public long getVerifiedReviewCountByProductId(Long productId) {
        log.debug("Getting verified review count for product ID: {}", productId);
        return reviewRepository.countVerifiedByProductId(productId);
    }

    @Override
    public Optional<ReviewResponseDto> getUserReviewForProduct(Long productId, Long userId) {
        log.debug("Getting user review for product ID: {} and user ID: {}", productId, userId);
        
        Optional<Review> review = reviewRepository.findByProductIdAndUserId(productId, userId);
        return review.map(entityMapper::toReviewResponseDto);
    }

    @Override
    public boolean hasUserReviewedProduct(Long productId, Long userId) {
        log.debug("Checking if user ID: {} has reviewed product ID: {}", userId, productId);
        return reviewRepository.existsByProductIdAndUserId(productId, userId);
    }

    @Override
    @Transactional
    public ReviewResponseDto markReviewHelpful(Long reviewId) {
        log.info("Marking review ID: {} as helpful", reviewId);
        
        reviewRepository.incrementHelpfulCount(reviewId);
        clearReviewCache(reviewId);
        
        Review review = findReviewById(reviewId);
        return entityMapper.toReviewResponseDto(review);
    }

    @Override
    @Transactional
    public ReviewResponseDto markReviewNotHelpful(Long reviewId) {
        log.info("Marking review ID: {} as not helpful", reviewId);
        
        reviewRepository.incrementNotHelpfulCount(reviewId);
        clearReviewCache(reviewId);
        
        Review review = findReviewById(reviewId);
        return entityMapper.toReviewResponseDto(review);
    }

    // Helper methods
    private Review findReviewById(Long id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Review not found with ID: " + id));
    }

    private Product findProductById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with ID: " + productId));
    }

    private PageResponse<ReviewResponseDto> mapToPageResponse(Page<Review> reviews) {
        List<ReviewResponseDto> content = reviews.getContent().stream()
                .map(entityMapper::toReviewResponseDto)
                .collect(Collectors.toList());
        
        return PageResponse.<ReviewResponseDto>builder()
                .content(content)
                .page(reviews.getNumber())
                .size(reviews.getSize())
                .totalElements(reviews.getTotalElements())
                .totalPages(reviews.getTotalPages())
                .first(reviews.isFirst())
                .last(reviews.isLast())
                .empty(reviews.isEmpty())
                .build();
    }

    private void updateReviewFields(Review review, ReviewUpdateDto updateDto) {
        if (updateDto.getRating() != null) {
            review.setRating(updateDto.getRating());
        }
        if (updateDto.getTitle() != null) {
            review.setTitle(updateDto.getTitle());
        }
        if (updateDto.getComment() != null) {
            review.setComment(updateDto.getComment());
        }
        if (updateDto.getStatus() != null) {
            review.setStatus(updateDto.getStatus());
            review.setModeratedAt(LocalDateTime.now());
        }
        if (updateDto.getModerationNotes() != null) {
            review.setModerationNotes(updateDto.getModerationNotes());
        }
    }

    // Placeholder implementations for remaining methods
    @Override public PageResponse<ReviewResponseDto> getReviewsByProductIdAndRating(Long productId, Integer rating, Pageable pageable) { return null; }
    @Override public PageResponse<ReviewResponseDto> getReviewsByProductIdAndRatingRange(Long productId, Integer minRating, Integer maxRating, Pageable pageable) { return null; }
    @Override public PageResponse<ReviewResponseDto> getVerifiedReviewsByProductId(Long productId, Pageable pageable) { return null; }
    @Override public List<ReviewResponseDto> getReviewsByProductIdSince(Long productId, LocalDateTime since) { return List.of(); }
    @Override public PageResponse<ReviewResponseDto> searchReviewsByProductId(Long productId, String query, Pageable pageable) { return null; }
    @Override public PageResponse<ReviewResponseDto> searchReviews(String query, Pageable pageable) { return null; }
    @Override public List<ReviewResponseDto> getHelpfulReviewsByProductId(Long productId, Integer minHelpfulCount) { return List.of(); }
    @Override public PageResponse<ReviewResponseDto> getUserReviewHistory(Long userId, Pageable pageable) { return null; }
    @Override public void validateReviewData(ReviewCreateDto createDto) { }
    @Override public void validateReviewData(ReviewUpdateDto updateDto, Long reviewId) { }
    @Override public boolean canUserReviewProduct(Long productId, Long userId) { return !hasUserReviewedProduct(productId, userId); }
    @Override public long getTotalReviewCount() { return reviewRepository.count(); }
    @Override public long getReviewCountByStatus(ReviewStatus status) { return reviewRepository.countByStatus(status); }
    @Override public long getPendingReviewCount() { return reviewRepository.countByStatus(ReviewStatus.PENDING); }
    @Override public long getApprovedReviewCount() { return reviewRepository.countByStatus(ReviewStatus.APPROVED); }
    @Override public long getRejectedReviewCount() { return reviewRepository.countByStatus(ReviewStatus.REJECTED); }
    @Override public List<Object[]> getReviewStatsByProductIds(List<Long> productIds) { return reviewRepository.findReviewSummaryByProductIds(productIds); }
    @Override public List<Object[]> getRatingDistributionByProductIds(List<Long> productIds) { return reviewRepository.findRatingDistributionByProductIds(productIds); }
    @Override public PageResponse<Object[]> getTopReviewers(Integer minReviews, Pageable pageable) { return null; }
    @Override public List<ReviewResponseDto> getRecentReviews(int limit) { return List.of(); }
    @Override public List<ReviewResponseDto> getReviewsByDateRange(LocalDateTime start, LocalDateTime end) { return List.of(); }
    @Override public PageResponse<ReviewResponseDto> getRecentApprovedReviews(Pageable pageable) { return null; }
    @Override public List<ReviewResponseDto> getReviewsByIds(List<Long> reviewIds) { return List.of(); }
    @Override public void bulkDeleteReviews(List<Long> reviewIds) { }
    @Override public List<ReviewResponseDto> bulkUpdateReviewStatus(List<Long> reviewIds, ReviewStatus status) { return List.of(); }
    @Override public List<ReviewResponseDto> getReviewsWithHighHelpfulnessRatio(Double minRatio) { return List.of(); }
    @Override public List<ReviewResponseDto> getReviewsWithLowHelpfulnessRatio(Double maxRatio) { return List.of(); }
    @Override public List<ReviewResponseDto> getSuspiciousReviews() { return List.of(); }
    @Override @CacheEvict(value = "reviews", allEntries = true) public void clearReviewCache() { }
    @Override @CacheEvict(value = "reviews", key = "#reviewId") public void clearReviewCache(Long reviewId) { }
    @Override @CacheEvict(value = "product_reviews", allEntries = true) public void clearProductReviewCache(Long productId) { }
    @Override public void refreshReviewCache(Long reviewId) { }
}
