package org.de013.productcatalog.mapper;

import org.de013.productcatalog.dto.review.ReviewResponseDto;
import org.de013.productcatalog.entity.Review;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper {

    public ReviewResponseDto toReviewResponseDto(Review review) {
        if (review == null) return null;

        return ReviewResponseDto.builder()
                .id(review.getId())
                .productId(review.getProduct().getId())
                .productName(review.getProduct().getName())
                .userId(review.getUserId())
                .rating(review.getRating())
                .title(review.getTitle())
                .comment(review.getComment())
                .status(review.getStatus())
                .helpfulCount(review.getHelpfulCount())
                .notHelpfulCount(review.getNotHelpfulCount())
                .verifiedPurchase(review.getVerifiedPurchase())
                .reviewerName(review.getReviewerName())
                .moderationNotes(review.getModerationNotes())
                .moderatedBy(review.getModeratedBy())
                .moderatedAt(review.getModeratedAt())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .createdBy(review.getCreatedBy())
                .helpfulnessRatio(calculateHelpfulnessRatio(review))
                .ratingStars(generateRatingStars(review.getRating()))
                .build();
    }

    private Double calculateHelpfulnessRatio(Review review) {
        int total = review.getHelpfulCount() + review.getNotHelpfulCount();
        if (total == 0) return null;
        return (double) review.getHelpfulCount() / total;
    }

    private String generateRatingStars(Integer rating) {
        if (rating == null) return "";
        StringBuilder stars = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            stars.append(i <= rating ? "★" : "☆");
        }
        return stars.toString();
    }
}

