package org.de013.productcatalog.dto.product;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Product image information")
public record ProductImageDto(

    @Schema(description = "Image ID", example = "1")
    Long id,

    @Schema(description = "Image URL", example = "https://example.com/images/iphone-15-pro-main.jpg")
    String url,

    @Schema(description = "Alt text", example = "iPhone 15 Pro front view")
    String altText,

    @Schema(description = "Image type", example = "MAIN")
    String imageType,

    @Schema(description = "Display order", example = "1")
    Integer displayOrder,

    @Schema(description = "Is active", example = "true")
    Boolean isActive,

    @Schema(description = "Image title", example = "iPhone 15 Pro")
    String title,

    @Schema(description = "Image description", example = "Front view of iPhone 15 Pro")
    String description,

    @Schema(description = "File size", example = "2.5MB")
    String fileSize,

    @Schema(description = "Image dimensions", example = "1920x1080")
    String dimensions,

    @Schema(description = "File format", example = "JPG")
    String fileFormat,

    @Schema(description = "Variant ID (if image is variant-specific)", example = "5")
    Long variantId
) {
}
