package org.de013.productcatalog.dto.product;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Product image information")
public class ProductImageDto {

    @Schema(description = "Image ID", example = "1")
    private Long id;

    @Schema(description = "Image URL", example = "https://example.com/images/iphone-15-pro-main.jpg")
    private String url;

    @Schema(description = "Alt text", example = "iPhone 15 Pro front view")
    private String altText;

    @Schema(description = "Image type", example = "MAIN")
    private String imageType;

    @Schema(description = "Display order", example = "1")
    private Integer displayOrder;

    @Schema(description = "Is active", example = "true")
    private Boolean isActive;

    @Schema(description = "Image title", example = "iPhone 15 Pro")
    private String title;

    @Schema(description = "Image description", example = "Front view of iPhone 15 Pro")
    private String description;

    @Schema(description = "File size", example = "2.5MB")
    private String fileSize;

    @Schema(description = "Image dimensions", example = "1920x1080")
    private String dimensions;

    @Schema(description = "File format", example = "JPG")
    private String fileFormat;

    @Schema(description = "Variant ID (if image is variant-specific)", example = "5")
    private Long variantId;
}
