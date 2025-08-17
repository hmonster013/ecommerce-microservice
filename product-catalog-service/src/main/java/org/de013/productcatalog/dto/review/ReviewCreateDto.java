package org.de013.productcatalog.dto.review;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Review creation request")
public class ReviewCreateDto {

    @NotNull(message = "Product ID is required")
    @Schema(description = "Product ID", example = "1", required = true)
    private Long productId;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    @Schema(description = "Rating (1-5)", example = "5", required = true)
    private Integer rating;

    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Schema(description = "Review title", example = "Amazing phone!")
    private String title;

    @NotBlank(message = "Comment is required")
    @Size(max = 2000, message = "Comment must not exceed 2000 characters")
    @Schema(description = "Review comment", example = "The iPhone 15 Pro exceeded my expectations...", required = true)
    private String comment;

    @Size(max = 255, message = "Reviewer name must not exceed 255 characters")
    @Schema(description = "Reviewer name", example = "John D.")
    private String reviewerName;

    @Email(message = "Reviewer email must be valid")
    @Size(max = 255, message = "Reviewer email must not exceed 255 characters")
    @Schema(description = "Reviewer email", example = "john.doe@example.com")
    private String reviewerEmail;

    @Schema(description = "Is verified purchase", example = "true")
    @Builder.Default
    private Boolean verifiedPurchase = false;

    // Validation method
    @AssertTrue(message = "Either reviewer name or email must be provided")
    public boolean isReviewerInfoValid() {
        return (reviewerName != null && !reviewerName.trim().isEmpty()) ||
               (reviewerEmail != null && !reviewerEmail.trim().isEmpty());
    }
}
