package org.de013.productcatalog.service;

import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.image.ProductImageCreateDto;
import org.de013.productcatalog.dto.image.ProductImageUpdateDto;
import org.de013.productcatalog.dto.product.ProductImageDto;
import org.de013.productcatalog.entity.enums.ImageType;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductImageService {

    // CRUD Operations
    ProductImageDto createImage(Long productId, ProductImageCreateDto createDto);
    
    ProductImageDto updateImage(Long imageId, ProductImageUpdateDto updateDto);
    
    void deleteImage(Long imageId);
    
    ProductImageDto getImageById(Long imageId);

    // Product-specific Operations
    List<ProductImageDto> getImagesByProductId(Long productId);
    
    List<ProductImageDto> getActiveImagesByProductId(Long productId);
    
    PageResponse<ProductImageDto> getImagesByProductId(Long productId, Pageable pageable);
    
    ProductImageDto getMainImageByProductId(Long productId);
    
    List<ProductImageDto> getGalleryImagesByProductId(Long productId);

    // Validation Operations
    boolean isMainImageExists(Long productId);

    boolean isMainImageExists(Long productId, Long excludeImageId);

    void validateImageData(ProductImageCreateDto createDto);

    void validateImageData(ProductImageUpdateDto updateDto, Long imageId);

    void validateMainImageConstraint(Long productId, ImageType imageType);

    void validateMainImageConstraint(Long productId, ImageType imageType, Long excludeImageId);

    // File Management Operations
    boolean isValidImageUrl(String url);

    String extractFileFormatFromUrl(String url);

    void validateImageUrl(String url);
}
