package org.de013.productcatalog.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.de013.productcatalog.entity.enums.ReviewStatus;

@Entity
@Table(name = "reviews", indexes = {
        @Index(name = "idx_reviews_product_id", columnList = "product_id"),
        @Index(name = "idx_reviews_user_id", columnList = "user_id"),
        @Index(name = "idx_reviews_status", columnList = "status"),
        @Index(name = "idx_reviews_rating", columnList = "rating"),
        @Index(name = "idx_reviews_created_at", columnList = "created_at"),
        @Index(name = "idx_reviews_helpful_count", columnList = "helpful_count")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Product is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @NotNull(message = "User ID is required")
    @Column(name = "user_id", nullable = false)
    private Long userId; // Reference to User from User Service

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Column(name = "title")
    private String title;

    @NotBlank(message = "Comment is required")
    @Size(max = 2000, message = "Comment must not exceed 2000 characters")
    @Column(name = "comment", nullable = false, columnDefinition = "TEXT")
    private String comment;

    @NotNull(message = "Review status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PENDING;

    @Column(name = "helpful_count", nullable = false)
    @Builder.Default
    private Integer helpfulCount = 0;

    @Column(name = "not_helpful_count", nullable = false)
    @Builder.Default
    private Integer notHelpfulCount = 0;

    @Column(name = "verified_purchase", nullable = false)
    @Builder.Default
    private Boolean verifiedPurchase = false;

    @Size(max = 255, message = "Reviewer name must not exceed 255 characters")
    @Column(name = "reviewer_name")
    private String reviewerName;

    @Size(max = 255, message = "Reviewer email must not exceed 255 characters")
    @Email(message = "Reviewer email must be valid")
    @Column(name = "reviewer_email")
    private String reviewerEmail;

    @Column(name = "moderation_notes", columnDefinition = "TEXT")
    private String moderationNotes;

    @Column(name = "moderated_by")
    private String moderatedBy;

    @Column(name = "moderated_at")
    private java.time.LocalDateTime moderatedAt;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

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

    public void approve(String moderatorId) {
        this.status = ReviewStatus.APPROVED;
        this.moderatedBy = moderatorId;
        this.moderatedAt = java.time.LocalDateTime.now();
    }

    public void reject(String moderatorId, String reason) {
        this.status = ReviewStatus.REJECTED;
        this.moderatedBy = moderatorId;
        this.moderatedAt = java.time.LocalDateTime.now();
        this.moderationNotes = reason;
    }

    public void flag(String reason) {
        this.status = ReviewStatus.FLAGGED;
        this.moderationNotes = reason;
    }

    public void markAsSpam(String moderatorId) {
        this.status = ReviewStatus.SPAM;
        this.moderatedBy = moderatorId;
        this.moderatedAt = java.time.LocalDateTime.now();
    }

    public void incrementHelpfulCount() {
        this.helpfulCount++;
    }

    public void incrementNotHelpfulCount() {
        this.notHelpfulCount++;
    }

    public double getHelpfulnessRatio() {
        int totalVotes = helpfulCount + notHelpfulCount;
        if (totalVotes == 0) {
            return 0.0;
        }
        return (double) helpfulCount / totalVotes;
    }

    public String getRatingStars() {
        if (rating == null) {
            return "☆☆☆☆☆";
        }
        StringBuilder stars = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            stars.append(i <= rating ? "★" : "☆");
        }
        return stars.toString();
    }

    @Override
    public String toString() {
        return "Review{" +
                "id=" + id +
                ", userId=" + userId +
                ", rating=" + rating +
                ", status=" + status +
                ", verifiedPurchase=" + verifiedPurchase +
                ", helpfulCount=" + helpfulCount +
                '}';
    }
}
