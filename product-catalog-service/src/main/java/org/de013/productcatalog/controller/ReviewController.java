package org.de013.productcatalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.constant.ApiPaths;
import org.de013.common.dto.ApiResponse;
import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.review.ReviewCreateDto;
import org.de013.productcatalog.dto.review.ReviewResponseDto;
import org.de013.productcatalog.dto.review.ReviewSummaryDto;
import org.de013.productcatalog.dto.review.ReviewUpdateDto;
import org.de013.productcatalog.entity.enums.ReviewStatus;
import org.de013.productcatalog.service.ReviewService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("") // Gateway routes /api/v1/products/** to /products/** - reviews are under products
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Product review management API")
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "Get product reviews", description = "Retrieve reviews for a specific product")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Reviews retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.REVIEWS)
    public ResponseEntity<org.de013.common.dto.ApiResponse<PageResponse<ReviewResponseDto>>> getProductReviews(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id,

            @Parameter(description = "Pagination and sorting parameters")
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,

            @Parameter(description = "Filter by rating")
            @RequestParam(required = false) Integer rating,

            @Parameter(description = "Show only verified purchases")
            @RequestParam(required = false, defaultValue = "false") Boolean verifiedOnly) {

        log.info("Getting reviews for product ID: {}, rating: {}, verifiedOnly: {}", id, rating, verifiedOnly);

        PageResponse<ReviewResponseDto> reviews;

        if (rating != null) {
            reviews = reviewService.getReviewsByProductIdAndRating(id, rating, pageable);
        } else if (verifiedOnly) {
            reviews = reviewService.getVerifiedReviewsByProductId(id, pageable);
        } else {
            reviews = reviewService.getApprovedReviewsByProductId(id, pageable);
        }

        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(reviews));
    }

    @Operation(summary = "Create product review", description = "Create a new review for a product (Authenticated users only)")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Review created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid review data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "User has already reviewed this product")
    })
    @PostMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.REVIEWS)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<org.de013.common.dto.ApiResponse<ReviewResponseDto>> createProductReview(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id,
            
            @Parameter(description = "Review creation data", required = true)
            @Valid @RequestBody ReviewCreateDto createDto,
            
            Authentication authentication) {
        
        log.info("Creating review for product ID: {} by user: {}", id, authentication.getName());
        
        // Set product ID and user ID from path and authentication
        createDto.setProductId(id);
        // In real implementation, extract user ID from authentication
        // createDto.setUserId(extractUserIdFromAuthentication(authentication));
        
        ReviewResponseDto review = reviewService.createReview(createDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(org.de013.common.dto.ApiResponse.success(review, "Review created successfully"));
    }

    @Operation(summary = "Update review", description = "Update an existing review (Review owner only)")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Review updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid review data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied - not review owner"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Review not found")
    })
    @PutMapping(ApiPaths.REVIEWS + ApiPaths.ID_PARAM)
    @PreAuthorize("isAuthenticated() and @reviewService.isReviewOwner(#id, authentication.name)")
    public ResponseEntity<org.de013.common.dto.ApiResponse<ReviewResponseDto>> updateReview(
            @Parameter(description = "Review ID", required = true)
            @PathVariable Long id,

            @Parameter(description = "Review update data", required = true)
            @Valid @RequestBody ReviewUpdateDto updateDto,

            Authentication authentication) {

        log.info("Updating review ID: {} by user: {}", id, authentication.getName());

        ReviewResponseDto review = reviewService.updateReview(id, updateDto);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(review, "Review updated successfully"));
    }

    @Operation(summary = "Delete review", description = "Delete a review (Review owner or Admin only)")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Review deleted successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Review not found")
    })
    @DeleteMapping(ApiPaths.REVIEWS + ApiPaths.ID_PARAM)
    @PreAuthorize("isAuthenticated() and (@reviewService.isReviewOwner(#id, authentication.name) or hasRole('ADMIN'))")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Void>> deleteReview(
            @Parameter(description = "Review ID", required = true)
            @PathVariable Long id,

            Authentication authentication) {

        log.info("Deleting review ID: {} by user: {}", id, authentication.getName());

        reviewService.deleteReview(id);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(null, "Review deleted successfully"));
    }

    @Operation(summary = "Moderate review", description = "Moderate a review (Admin only)")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Review moderated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid moderation data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Review not found")
    })
    @PutMapping(ApiPaths.REVIEWS + ApiPaths.ID_PARAM + ApiPaths.MODERATE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<ReviewResponseDto>> moderateReview(
            @Parameter(description = "Review ID", required = true)
            @PathVariable Long id,
            
            @Parameter(description = "New review status", required = true)
            @RequestParam ReviewStatus status,
            
            @Parameter(description = "Moderation reason")
            @RequestParam(required = false) String reason,
            
            Authentication authentication) {
        
        log.info("Moderating review ID: {} to status: {} by admin: {}", id, status, authentication.getName());
        
        ReviewResponseDto review = switch (status) {
            case APPROVED -> reviewService.approveReview(id, authentication.getName());
            case REJECTED -> reviewService.rejectReview(id, authentication.getName(), reason);
            case FLAGGED -> reviewService.flagReview(id, authentication.getName(), reason);
            default -> throw new IllegalArgumentException("Invalid moderation status: " + status);
        };
        
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(review, "Review moderated successfully"));
    }

    @Operation(summary = "Get review summary", description = "Get review statistics and summary for a product")
    @GetMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.REVIEWS + ApiPaths.SUMMARY)
    public ResponseEntity<org.de013.common.dto.ApiResponse<ReviewSummaryDto>> getReviewSummary(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id) {

        log.info("Getting review summary for product ID: {}", id);

        ReviewSummaryDto summary = reviewService.getReviewSummaryByProductId(id);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(summary));
    }

    @Operation(summary = "Get recent reviews", description = "Get recent reviews for a product")
    @GetMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.REVIEWS + ApiPaths.RECENT)
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<ReviewResponseDto>>> getRecentReviews(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id,

            @Parameter(description = "Maximum number of reviews to return")
            @RequestParam(required = false, defaultValue = "5") Integer limit) {

        log.info("Getting recent reviews for product ID: {} with limit: {}", id, limit);

        List<ReviewResponseDto> reviews = reviewService.getRecentReviewsByProductId(id, limit);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(reviews));
    }

    @Operation(summary = "Get helpful reviews", description = "Get most helpful reviews for a product")
    @GetMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.REVIEWS + ApiPaths.HELPFUL)
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<ReviewResponseDto>>> getHelpfulReviews(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id,

            @Parameter(description = "Maximum number of reviews to return")
            @RequestParam(required = false, defaultValue = "5") Integer limit) {

        log.info("Getting helpful reviews for product ID: {} with limit: {}", id, limit);

        List<ReviewResponseDto> reviews = reviewService.getMostHelpfulReviewsByProductId(id, limit);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(reviews));
    }

    @Operation(summary = "Mark review as helpful", description = "Mark a review as helpful")
    @PostMapping(ApiPaths.REVIEWS + ApiPaths.ID_PARAM + ApiPaths.HELPFUL)
    public ResponseEntity<org.de013.common.dto.ApiResponse<ReviewResponseDto>> markReviewHelpful(
            @Parameter(description = "Review ID", required = true)
            @PathVariable Long id) {

        log.info("Marking review ID: {} as helpful", id);

        ReviewResponseDto review = reviewService.markReviewHelpful(id);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(review, "Review marked as helpful"));
    }

    @Operation(summary = "Mark review as not helpful", description = "Mark a review as not helpful")
    @PostMapping(ApiPaths.REVIEWS + ApiPaths.ID_PARAM + ApiPaths.NOT_HELPFUL)
    public ResponseEntity<org.de013.common.dto.ApiResponse<ReviewResponseDto>> markReviewNotHelpful(
            @Parameter(description = "Review ID", required = true)
            @PathVariable Long id) {

        log.info("Marking review ID: {} as not helpful", id);

        ReviewResponseDto review = reviewService.markReviewNotHelpful(id);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(review, "Review marked as not helpful"));
    }

    // Admin endpoints
    @Operation(summary = "Get reviews needing moderation", description = "Get reviews that need moderation (Admin only)")
    @GetMapping(ApiPaths.REVIEWS + ApiPaths.MODERATION)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<PageResponse<ReviewResponseDto>>> getReviewsNeedingModeration(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {

        log.info("Getting reviews needing moderation");

        PageResponse<ReviewResponseDto> reviews = reviewService.getReviewsNeedingModeration(pageable);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(reviews));
    }

    @Operation(summary = "Bulk moderate reviews", description = "Moderate multiple reviews at once (Admin only)")
    @PostMapping(ApiPaths.REVIEWS + ApiPaths.MODERATE + ApiPaths.BULK)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<ReviewResponseDto>>> bulkModerateReviews(
            @RequestParam List<Long> reviewIds,
            @RequestParam ReviewStatus status,
            Authentication authentication) {
        
        log.info("Bulk moderating {} reviews to status: {} by admin: {}", 
                reviewIds.size(), status, authentication.getName());
        
        List<ReviewResponseDto> reviews = reviewService.bulkModerateReviews(reviewIds, status, authentication.getName());
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(reviews, 
                String.format("Moderated %d reviews successfully", reviewIds.size())));
    }

    @Operation(summary = "Search reviews", description = "Search reviews by content")
    @GetMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.REVIEWS + ApiPaths.SEARCH)
    public ResponseEntity<org.de013.common.dto.ApiResponse<PageResponse<ReviewResponseDto>>> searchProductReviews(
            @PathVariable Long id,
            @RequestParam String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("Searching reviews for product ID: {} with query: {}", id, q);

        PageResponse<ReviewResponseDto> reviews = reviewService.searchReviewsByProductId(id, q, pageable);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(reviews));
    }
}
