package org.de013.productcatalog.dto.product;

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
@Schema(description = "Product variant group (variants of same type)")
public class ProductVariantGroupDto {

    @Schema(description = "Variant type", example = "COLOR")
    private String variantType;

    @Schema(description = "Variant type display name", example = "Color")
    private String displayName;

    @Schema(description = "Variant type description", example = "Product color variations")
    private String description;

    @Schema(description = "List of variants in this group")
    private List<ProductVariantDto> variants;

    @Schema(description = "Is required selection", example = "true")
    private Boolean required;

    @Schema(description = "Allow multiple selection", example = "false")
    private Boolean multipleSelection;

    @Schema(description = "Display order", example = "1")
    private Integer displayOrder;
}
