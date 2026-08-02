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

        return new ProductImageDto(
                image.getId(),
                image.getUrl(),
                image.getAltText(),
                image.getImageType().name(),
                image.getDisplayOrder(),
                image.getIsActive(),
                image.getTitle(),
                image.getDescription(),
                image.getFileSize(),
                image.getDimensions(),
                image.getFileFormat(),
                image.getVariant() != null ? image.getVariant().getId() : null
        );
    }

    public List<ProductImageDto> toProductImageDtos(List<ProductImage> images) {
        if (images == null) return null;
        return images.stream()
                .map(this::toProductImageDto)
                .collect(Collectors.toList());
    }
}

