package org.de013.productcatalog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.PageResponse;
import org.de013.common.exception.ResourceNotFoundException;
import org.de013.productcatalog.dto.image.ProductImageCreateDto;
import org.de013.productcatalog.dto.image.ProductImageUpdateDto;
import org.de013.productcatalog.dto.product.ProductImageDto;
import org.de013.productcatalog.entity.Product;
import org.de013.productcatalog.entity.ProductImage;
import org.de013.productcatalog.entity.ProductVariant;
import org.de013.productcatalog.entity.enums.ImageType;
import org.de013.productcatalog.mapper.ProductImageMapper;
import org.de013.productcatalog.repository.ProductImageRepository;
import org.de013.productcatalog.repository.ProductRepository;
import org.de013.productcatalog.repository.ProductVariantRepository;
import org.de013.productcatalog.service.ProductImageService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductImageRepository imageRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageMapper imageMapper;

    @Override
    @Transactional
    public ProductImageDto createImage(Long productId, ProductImageCreateDto createDto) {
        log.info("Creating image for product ID: {}, type: {}",
                productId, createDto.getImageType());

        validateImageData(createDto);

        // Find product
        Product product = findProductById(productId);

        // Find variant if specified
        ProductVariant variant = null;
        if (createDto.getVariantId() != null) {
            variant = findVariantById(createDto.getVariantId());
            // Ensure variant belongs to the same product
            if (!variant.getProduct().getId().equals(productId)) {
                throw new IllegalArgumentException("Variant does not belong to the specified product");
            }
        }

        // Validate MAIN image constraint
        if (createDto.getImageType() == ImageType.MAIN) {
            validateMainImageConstraint(productId, ImageType.MAIN);
        }

        // Extract file format from URL if not provided
        String fileFormat = createDto.getFileFormat();
        if (!StringUtils.hasText(fileFormat)) {
            fileFormat = extractFileFormatFromUrl(createDto.getUrl());
        }

        // Create image entity
        ProductImage image = ProductImage.builder()
                .product(product)
                .url(createDto.getUrl())
                .altText(createDto.getAltText())
                .imageType(createDto.getImageType())
                .displayOrder(createDto.getDisplayOrder())
                .isActive(createDto.getIsActive())
                .title(createDto.getTitle())
                .description(createDto.getDescription())
                .fileSize(createDto.getFileSize())
                .dimensions(createDto.getDimensions())
                .fileFormat(fileFormat)
                .variant(variant)
                .build();

        ProductImage savedImage = imageRepository.save(image);
        log.info("Image created successfully with ID: {}", savedImage.getId());

        return imageMapper.toProductImageDto(savedImage);
    }

    @Override
    @Transactional
    @CacheEvict(value = "images", key = "#imageId")
    public ProductImageDto updateImage(Long imageId, ProductImageUpdateDto updateDto) {
        log.info("Updating image with ID: {}", imageId);

        validateImageData(updateDto, imageId);

        ProductImage image = findImageById(imageId);

        // Update fields if provided
        if (StringUtils.hasText(updateDto.getUrl())) {
            validateImageUrl(updateDto.getUrl());
            image.setUrl(updateDto.getUrl());
            
            // Update file format if URL changed and format not explicitly provided
            if (updateDto.getFileFormat() == null) {
                image.setFileFormat(extractFileFormatFromUrl(updateDto.getUrl()));
            }
        }

        if (updateDto.getAltText() != null) {
            image.setAltText(updateDto.getAltText());
        }

        if (updateDto.getImageType() != null) {
            // Validate MAIN image constraint if changing to MAIN
            if (updateDto.getImageType() == ImageType.MAIN && image.getImageType() != ImageType.MAIN) {
                validateMainImageConstraint(image.getProduct().getId(), ImageType.MAIN, imageId);
            }
            image.setImageType(updateDto.getImageType());
        }

        if (updateDto.getDisplayOrder() != null) {
            image.setDisplayOrder(updateDto.getDisplayOrder());
        }

        if (updateDto.getIsActive() != null) {
            image.setIsActive(updateDto.getIsActive());
        }

        if (updateDto.getTitle() != null) {
            image.setTitle(updateDto.getTitle());
        }

        if (updateDto.getDescription() != null) {
            image.setDescription(updateDto.getDescription());
        }

        if (updateDto.getFileSize() != null) {
            image.setFileSize(updateDto.getFileSize());
        }

        if (updateDto.getDimensions() != null) {
            image.setDimensions(updateDto.getDimensions());
        }

        if (updateDto.getFileFormat() != null) {
            image.setFileFormat(updateDto.getFileFormat());
        }

        if (updateDto.getVariantId() != null) {
            ProductVariant variant = findVariantById(updateDto.getVariantId());
            // Ensure variant belongs to the same product
            if (!variant.getProduct().getId().equals(image.getProduct().getId())) {
                throw new IllegalArgumentException("Variant does not belong to the same product as the image");
            }
            image.setVariant(variant);
        }

        ProductImage savedImage = imageRepository.save(image);
        log.info("Image updated successfully with ID: {}", savedImage.getId());

        return imageMapper.toProductImageDto(savedImage);
    }

    @Override
    @Transactional
    @CacheEvict(value = "images", key = "#imageId")
    public void deleteImage(Long imageId) {
        log.info("Deleting image with ID: {}", imageId);

        ProductImage image = findImageById(imageId);
        imageRepository.delete(image);

        log.info("Image deleted successfully with ID: {}", imageId);
    }

    @Override
    @Cacheable(value = "images", key = "#imageId")
    public ProductImageDto getImageById(Long imageId) {
        log.debug("Getting image by ID: {}", imageId);

        ProductImage image = findImageById(imageId);
        return imageMapper.toProductImageDto(image);
    }

    @Override
    public List<ProductImageDto> getImagesByProductId(Long productId) {
        log.debug("Getting images for product ID: {}", productId);

        List<ProductImage> images = imageRepository.findByProductId(productId);
        return imageMapper.toProductImageDtos(images);
    }

    @Override
    public List<ProductImageDto> getActiveImagesByProductId(Long productId) {
        log.debug("Getting active images for product ID: {}", productId);

        List<ProductImage> images = imageRepository.findByProductIdAndIsActiveTrueOrderByDisplayOrderAsc(productId);
        return imageMapper.toProductImageDtos(images);
    }

    @Override
    public PageResponse<ProductImageDto> getImagesByProductId(Long productId, Pageable pageable) {
        log.debug("Getting images for product ID: {} with pagination", productId);

        Page<ProductImage> imagePage = imageRepository.findByProductId(productId, pageable);
        List<ProductImageDto> imageDtos = imageMapper.toProductImageDtos(imagePage.getContent());

        return PageResponse.<ProductImageDto>builder()
                .content(imageDtos)
                .page(imagePage.getNumber())
                .size(imagePage.getSize())
                .totalElements(imagePage.getTotalElements())
                .totalPages(imagePage.getTotalPages())
                .first(imagePage.isFirst())
                .last(imagePage.isLast())
                .build();
    }

    @Override
    public ProductImageDto getMainImageByProductId(Long productId) {
        log.debug("Getting main image for product ID: {}", productId);

        Optional<ProductImage> mainImage = imageRepository.findMainImageByProductId(productId);
        return mainImage.map(imageMapper::toProductImageDto).orElse(null);
    }

    @Override
    public List<ProductImageDto> getGalleryImagesByProductId(Long productId) {
        log.debug("Getting gallery images for product ID: {}", productId);

        List<ProductImage> images = imageRepository.findGalleryImagesByProductId(productId);
        return imageMapper.toProductImageDtos(images);
    }

    // Helper methods
    private Product findProductById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));
    }

    private ProductVariant findVariantById(Long variantId) {
        return variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found with ID: " + variantId));
    }

    private ProductImage findImageById(Long imageId) {
        return imageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found with ID: " + imageId));
    }

    @Override
    public void validateImageData(ProductImageCreateDto createDto) {
        if (createDto == null) {
            throw new IllegalArgumentException("Image data cannot be null");
        }
        // Product ID validation removed - now handled via path parameter

        if (!StringUtils.hasText(createDto.getUrl())) {
            throw new IllegalArgumentException("Image URL is required");
        }

        validateImageUrl(createDto.getUrl());
    }

    @Override
    public void validateImageData(ProductImageUpdateDto updateDto, Long imageId) {
        if (updateDto == null) {
            throw new IllegalArgumentException("Image update data cannot be null");
        }

        if (!updateDto.hasUpdates()) {
            throw new IllegalArgumentException("At least one field must be provided for update");
        }

        if (!imageRepository.existsById(imageId)) {
            throw new ResourceNotFoundException("Image not found with ID: " + imageId);
        }

        if (StringUtils.hasText(updateDto.getUrl())) {
            validateImageUrl(updateDto.getUrl());
        }
    }

    @Override
    public void validateMainImageConstraint(Long productId, ImageType imageType) {
        if (imageType == ImageType.MAIN && isMainImageExists(productId)) {
            throw new IllegalArgumentException("Product already has a main image. Only one main image is allowed per product.");
        }
    }

    @Override
    public void validateMainImageConstraint(Long productId, ImageType imageType, Long excludeImageId) {
        if (imageType == ImageType.MAIN && isMainImageExists(productId, excludeImageId)) {
            throw new IllegalArgumentException("Product already has a main image. Only one main image is allowed per product.");
        }
    }

    @Override
    public boolean isMainImageExists(Long productId) {
        return imageRepository.findMainImageByProductId(productId).isPresent();
    }

    @Override
    public boolean isMainImageExists(Long productId, Long excludeImageId) {
        return imageRepository.findMainImageByProductId(productId)
                .map(image -> !image.getId().equals(excludeImageId))
                .orElse(false);
    }

    @Override
    public boolean isValidImageUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        return url.matches("^https?://.*\\.(jpg|jpeg|png|gif|webp)$");
    }

    @Override
    public String extractFileFormatFromUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        
        String extension = url.substring(url.lastIndexOf('.') + 1).toUpperCase();
        return extension.equals("JPEG") ? "JPG" : extension;
    }

    @Override
    public void validateImageUrl(String url) {
        if (!isValidImageUrl(url)) {
            throw new IllegalArgumentException("Invalid image URL format. URL must be HTTP/HTTPS and end with image extension (jpg, jpeg, png, gif, webp)");
        }
    }
}
