package org.de013.productcatalog.dto.image;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import org.de013.productcatalog.entity.enums.ImageType;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Product image creation request")
public record ProductImageCreateDto(

    @JsonIgnore
    Long productId,

    @NotBlank(message = "{image.url.required}")
    @Size(max = 500, message = "{image.url.too.long}")
    @Pattern(regexp = "^https?://.*\\.(jpg|jpeg|png|gif|webp)$",
            message = "{image.url.invalid.format}")
    @Schema(description = "Image URL", example = "https://example.com/images/iphone-15-pro-main.jpg", required = true)
    String url,

    @Size(max = 255, message = "{image.alt.text.too.long}")
    @Schema(description = "Alt text for accessibility", example = "iPhone 15 Pro front view")
    String altText,

    @NotNull(message = "{image.type.required}")
    @Schema(description = "Image type", example = "MAIN", required = true)
    ImageType imageType,

    @Min(value = 0, message = "{field.non.negative.required}")
    @Schema(description = "Display order", example = "1")
    Integer displayOrder,

    @Schema(description = "Is active", example = "true")
    Boolean isActive,

    @Size(max = 255, message = "{image.title.too.long}")
    @Schema(description = "Image title", example = "iPhone 15 Pro")
    String title,

    @Size(max = 2000, message = "{image.description.too.long}")
    @Schema(description = "Image description", example = "Front view of iPhone 15 Pro showing the premium design")
    String description,

    @Size(max = 100, message = "{image.file.size.too.long}")
    @Pattern(regexp = "^\\d+(\\.\\d+)?(KB|MB|GB)$", message = "{image.file.size.invalid.format}")
    @Schema(description = "File size", example = "2.5MB")
    String fileSize,

    @Size(max = 50, message = "{image.dimensions.too.long}")
    @Pattern(regexp = "^\\d+x\\d+$", message = "{image.dimensions.invalid.format}")
    @Schema(description = "Image dimensions", example = "1920x1080")
    String dimensions,

    @Size(max = 10, message = "{image.file.format.too.long}")
    @Pattern(regexp = "^(JPG|JPEG|PNG|GIF|WEBP)$", message = "{image.file.format.invalid}")
    @Schema(description = "File format", example = "JPG")
    String fileFormat,

    @Schema(description = "Variant ID (if image is variant-specific)", example = "5")
    Long variantId
) {
    public ProductImageCreateDto {
        if (imageType == null) {
            imageType = ImageType.GALLERY;
        }
        if (displayOrder == null) {
            displayOrder = 0;
        }
        if (isActive == null) {
            isActive = true;
        }
    }

    // Validation methods
    @JsonIgnore
    @AssertTrue(message = "Image URL must be a valid HTTP/HTTPS URL ending with image extension")
    public boolean isUrlValid() {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        return url.matches("^https?://.*\\.(jpg|jpeg|png|gif|webp)$");
    }

    @JsonIgnore
    @AssertTrue(message = "File format must match URL extension")
    public boolean isFileFormatConsistent() {
        if (fileFormat == null || url == null) {
            return true; // Optional field
        }

        String urlExtension = url.substring(url.lastIndexOf('.') + 1).toUpperCase();
        return fileFormat.equalsIgnoreCase(urlExtension) ||
                (fileFormat.equals("JPG") && urlExtension.equals("JPEG")) ||
                (fileFormat.equals("JPEG") && urlExtension.equals("JPG"));
    }

    @JsonIgnore
    @AssertTrue(message = "Only one MAIN image is allowed per product")
    public boolean isMainImageValid() {
        // This will be validated at service level against database
        return true;
    }
}
