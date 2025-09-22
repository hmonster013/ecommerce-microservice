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

    // Image Type Operations
    List<ProductImageDto> getImagesByProductIdAndType(Long productId, ImageType imageType);
    
    List<ProductImageDto> getActiveImagesByProductIdAndType(Long productId, ImageType imageType);
    
    ProductImageDto getFirstImageByProductIdAndType(Long productId, ImageType imageType);

    // Variant-specific Operations
    List<ProductImageDto> getImagesByVariantId(Long variantId);
    
    List<ProductImageDto> getActiveImagesByVariantId(Long variantId);
    
    List<ProductImageDto> getImagesByProductIdAndVariantId(Long productId, Long variantId);

    // Search and Filter Operations
    List<ProductImageDto> searchImagesByProductIdAndQuery(Long productId, String query);
    
    List<ProductImageDto> getImagesByFileFormat(Long productId, String fileFormat);
    
    List<ProductImageDto> getImagesByFileFormat(String fileFormat);

    // Bulk Operations
    List<ProductImageDto> createImages(List<ProductImageCreateDto> createDtos);
    
    List<ProductImageDto> updateImages(List<Long> imageIds, List<ProductImageUpdateDto> updateDtos);
    
    void deleteImages(List<Long> imageIds);
    
    void activateImages(List<Long> imageIds);
    
    void deactivateImages(List<Long> imageIds);
    
    List<ProductImageDto> getImagesByProductIds(List<Long> productIds);
    
    List<ProductImageDto> getMainImagesByProductIds(List<Long> productIds);

    // Validation Operations
    boolean isMainImageExists(Long productId);
    
    boolean isMainImageExists(Long productId, Long excludeImageId);
    
    void validateImageData(ProductImageCreateDto createDto);
    
    void validateImageData(ProductImageUpdateDto updateDto, Long imageId);
    
    void validateMainImageConstraint(Long productId, ImageType imageType);
    
    void validateMainImageConstraint(Long productId, ImageType imageType, Long excludeImageId);

    // Statistics Operations
    long getImageCountByProductId(Long productId);
    
    long getActiveImageCountByProductId(Long productId);
    
    long getImageCountByType(Long productId, ImageType imageType);
    
    List<Object[]> getImageCountsByType(Long productId);

    // Display Order Operations
    void reorderImages(Long productId, List<Long> imageIds);
    
    void moveImageUp(Long imageId);
    
    void moveImageDown(Long imageId);
    
    void setMainImage(Long imageId);
    
    void unsetMainImage(Long productId);

    // File Management Operations
    boolean isValidImageUrl(String url);
    
    String extractFileFormatFromUrl(String url);
    
    void validateImageUrl(String url);
}
