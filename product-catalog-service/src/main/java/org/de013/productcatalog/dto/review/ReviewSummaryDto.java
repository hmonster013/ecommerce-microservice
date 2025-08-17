package org.de013.productcatalog.dto.review;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Review summary")
public class ReviewSummaryDto {

    @Schema(description = "Average rating", example = "4.5")
    private Double averageRating;

    @Schema(description = "Total review count", example = "150")
    private Integer totalReviews;

    @Schema(description = "Verified purchase reviews count", example = "120")
    private Integer verifiedReviews;

    @Schema(description = "Rating distribution")
    private List<RatingCount> ratingDistribution;

    @Schema(description = "Review statistics")
    private ReviewStats stats;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Rating count")
    public static class RatingCount {
        
        @Schema(description = "Rating value", example = "5")
        private Integer rating;
        
        @Schema(description = "Count", example = "75")
        private Integer count;
        
        @Schema(description = "Percentage", example = "50.0")
        private Double percentage;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Review statistics")
    public static class ReviewStats {
        
        @Schema(description = "Positive reviews (4-5 stars)", example = "120")
        private Integer positiveReviews;
        
        @Schema(description = "Neutral reviews (3 stars)", example = "20")
        private Integer neutralReviews;
        
        @Schema(description = "Negative reviews (1-2 stars)", example = "10")
        private Integer negativeReviews;
        
        @Schema(description = "Positive percentage", example = "80.0")
        private Double positivePercentage;
        
        @Schema(description = "Would recommend percentage", example = "85.0")
        private Double recommendationPercentage;
    }

    // Helper methods
    public String getAverageRatingDisplay() {
        if (averageRating == null) {
            return "No ratings";
        }
        return String.format("%.1f", averageRating);
    }

    public String getRatingStars() {
        if (averageRating == null) {
            return "☆☆☆☆☆";
        }
        int fullStars = averageRating.intValue();
        boolean hasHalfStar = (averageRating - fullStars) >= 0.5;
        
        StringBuilder stars = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            if (i <= fullStars) {
                stars.append("★");
            } else if (i == fullStars + 1 && hasHalfStar) {
                stars.append("☆"); // Could use half star symbol
            } else {
                stars.append("☆");
            }
        }
        return stars.toString();
    }

    public boolean hasReviews() {
        return totalReviews != null && totalReviews > 0;
    }
}
