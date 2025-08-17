package org.de013.productcatalog.dto.review;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;
import org.de013.productcatalog.entity.enums.ReviewStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Review update request")
public class ReviewUpdateDto {

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    @Schema(description = "Rating (1-5)", example = "5")
    private Integer rating;

    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Schema(description = "Review title", example = "Amazing phone!")
    private String title;

    @Size(max = 2000, message = "Comment must not exceed 2000 characters")
    @Schema(description = "Review comment", example = "The iPhone 15 Pro exceeded my expectations...")
    private String comment;

    @Schema(description = "Review status (admin only)", example = "APPROVED")
    private ReviewStatus status;

    @Schema(description = "Moderation notes (admin only)", example = "Approved after review")
    private String moderationNotes;

    // Helper method to check if any field is set
    public boolean hasUpdates() {
        return rating != null || title != null || comment != null || 
               status != null || moderationNotes != null;
    }

    // Helper method to check if this is a moderation update
    public boolean isModerationUpdate() {
        return status != null || moderationNotes != null;
    }

    // Helper method to check if this is a user update
    public boolean isUserUpdate() {
        return rating != null || title != null || comment != null;
    }
}
