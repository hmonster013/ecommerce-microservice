package org.de013.productcatalog.mapper;

import org.de013.productcatalog.dto.product.ProductImageDto;
import org.de013.productcatalog.entity.ProductImage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ProductImageMapper {

    public ProductImageDto toProductImageDto(ProductImage image) {
        if (image == null) return null;

        return ProductImageDto.builder()
                .id(image.getId())
                .url(image.getUrl())
                .altText(image.getAltText())
                .imageType(image.getImageType().name())
                .displayOrder(image.getDisplayOrder())
                .isActive(image.getIsActive())
                .title(image.getTitle())
                .description(image.getDescription())
                .fileSize(image.getFileSize())
                .dimensions(image.getDimensions())
                .fileFormat(image.getFileFormat())
                .variantId(image.getVariant() != null ? image.getVariant().getId() : null)
                .build();
    }

    public List<ProductImageDto> toProductImageDtos(List<ProductImage> images) {
        if (images == null) return null;
        return images.stream()
                .map(this::toProductImageDto)
                .collect(Collectors.toList());
    }
}

