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

    @NotNull(message = "{review.product.required}")
    @Schema(description = "Product ID", example = "1", required = true)
    private Long productId;

    @NotNull(message = "{field.required}")
    @Schema(description = "User ID", example = "1001", required = true)
    private Long userId;

    @NotNull(message = "{review.rating.required}")
    @Min(value = 1, message = "{review.rating.invalid}")
    @Max(value = 5, message = "{review.rating.invalid}")
    @Schema(description = "Rating (1-5)", example = "5", required = true)
    private Integer rating;

    @Size(max = 255, message = "{review.title.too.long}")
    @Schema(description = "Review title", example = "Amazing phone!")
    private String title;

    @NotBlank(message = "{review.comment.required}")
    @Size(max = 2000, message = "{review.comment.too.long}")
    @Schema(description = "Review comment", example = "The iPhone 15 Pro exceeded my expectations...", required = true)
    private String comment;

    @Size(max = 255, message = "{review.reviewer.name.too.long}")
    @Schema(description = "Reviewer name", example = "John D.")
    private String reviewerName;

    @Email(message = "{review.reviewer.email.invalid}")
    @Size(max = 255, message = "{field.too.long}")
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
