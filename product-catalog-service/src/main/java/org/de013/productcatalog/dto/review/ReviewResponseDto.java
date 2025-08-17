package org.de013.productcatalog.dto.review;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.de013.productcatalog.entity.enums.ReviewStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Review response")
public class ReviewResponseDto {

    @Schema(description = "Review ID", example = "1")
    private Long id;

    @Schema(description = "Product ID", example = "1")
    private Long productId;

    @Schema(description = "Product name", example = "iPhone 15 Pro")
    private String productName;

    @Schema(description = "User ID", example = "1001")
    private Long userId;

    @Schema(description = "Rating (1-5)", example = "5")
    private Integer rating;

    @Schema(description = "Review title", example = "Amazing phone!")
    private String title;

    @Schema(description = "Review comment", example = "The iPhone 15 Pro exceeded my expectations...")
    private String comment;

    @Schema(description = "Review status", example = "APPROVED")
    private ReviewStatus status;

    @Schema(description = "Helpful count", example = "15")
    private Integer helpfulCount;

    @Schema(description = "Not helpful count", example = "2")
    private Integer notHelpfulCount;

    @Schema(description = "Is verified purchase", example = "true")
    private Boolean verifiedPurchase;

    @Schema(description = "Reviewer name", example = "John D.")
    private String reviewerName;

    @Schema(description = "Moderation notes", example = "Approved after review")
    private String moderationNotes;

    @Schema(description = "Moderated by", example = "admin")
    private String moderatedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "Moderation timestamp", example = "2024-01-15 11:00:00")
    private LocalDateTime moderatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "Creation timestamp", example = "2024-01-15 10:30:00")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "Last update timestamp", example = "2024-01-15 10:30:00")
    private LocalDateTime updatedAt;

    @Schema(description = "Created by", example = "user123")
    private String createdBy;

    @Schema(description = "Helpfulness ratio", example = "0.88")
    private Double helpfulnessRatio;

    @Schema(description = "Rating stars display", example = "★★★★★")
    private String ratingStars;

    // Helper methods
    public boolean isVisible() {
        return status != null && status.isVisible();
    }

    public boolean needsModeration() {
        return status != null && status.needsModeration();
    }

    public boolean isPositive() {
        return rating != null && rating >= 4;
    }

    public boolean isNegative() {
        return rating != null && rating <= 2;
    }

    public boolean isNeutral() {
        return rating != null && rating == 3;
    }

    public String getDisplayName() {
        if (reviewerName != null && !reviewerName.trim().isEmpty()) {
            return reviewerName;
        }
        return "Anonymous";
    }

    public String getTimeAgo() {
        if (createdAt == null) {
            return "Unknown";
        }
        
        LocalDateTime now = LocalDateTime.now();
        long days = java.time.Duration.between(createdAt, now).toDays();
        
        if (days == 0) {
            return "Today";
        } else if (days == 1) {
            return "Yesterday";
        } else if (days < 30) {
            return days + " days ago";
        } else if (days < 365) {
            long months = days / 30;
            return months + " month" + (months > 1 ? "s" : "") + " ago";
        } else {
            long years = days / 365;
            return years + " year" + (years > 1 ? "s" : "") + " ago";
        }
    }
}
