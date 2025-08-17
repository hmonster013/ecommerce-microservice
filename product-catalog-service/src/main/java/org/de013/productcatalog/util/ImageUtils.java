package org.de013.productcatalog.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Utility class for image handling and validation.
 * Provides methods for image URL validation, format checking, and image processing.
 */
@Slf4j
@UtilityClass
public class ImageUtils {

    private static final Set<String> SUPPORTED_FORMATS = Set.of(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg"
    );

    private static final Set<String> PREFERRED_FORMATS = Set.of(
        "jpg", "jpeg", "png", "webp"
    );

    private static final Pattern URL_PATTERN = Pattern.compile(
        "^(https?://)([\\w\\-\\.]+)\\.([a-z]{2,6})(:[0-9]{1,5})?(/.*)?$",
        Pattern.CASE_INSENSITIVE
    );

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_DIMENSION = 4096; // 4K resolution

    /**
     * Validate image URL format and accessibility.
     */
    public static boolean isValidImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return false;
        }

        if (!URL_PATTERN.matcher(imageUrl).matches()) {
            log.debug("Invalid URL format: {}", imageUrl);
            return false;
        }

        String extension = extractFileExtension(imageUrl);
        if (!SUPPORTED_FORMATS.contains(extension.toLowerCase())) {
            log.debug("Unsupported image format: {} for URL: {}", extension, imageUrl);
            return false;
        }

        return !containsSuspiciousPatterns(imageUrl);
    }

    /**
     * Extract file extension from URL or filename.
     */
    public static String extractFileExtension(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "";
        }

        String cleanUrl = url.split("\\?")[0].split("#")[0];
        int lastDotIndex = cleanUrl.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == cleanUrl.length() - 1) {
            return "";
        }

        return cleanUrl.substring(lastDotIndex + 1);
    }

    /**
     * Check if image format is supported.
     */
    public static boolean isSupportedFormat(String extension) {
        if (extension == null) {
            return false;
        }
        return SUPPORTED_FORMATS.contains(extension.toLowerCase());
    }

    /**
     * Generate thumbnail URL from original image URL.
     */
    public static String generateThumbnailUrl(String originalUrl, String size) {
        if (!isValidImageUrl(originalUrl)) {
            return originalUrl;
        }

        String extension = extractFileExtension(originalUrl);
        String baseUrl = originalUrl.substring(0, originalUrl.lastIndexOf('.'));
        
        return String.format("%s_thumb_%s.%s", baseUrl, size, extension);
    }

    /**
     * Generate multiple thumbnail sizes.
     */
    public static Map<String, String> generateThumbnails(String originalUrl) {
        Map<String, String> thumbnails = new HashMap<>();
        
        if (!isValidImageUrl(originalUrl)) {
            return thumbnails;
        }

        String[] sizes = {"50x50", "150x150", "300x300", "600x600"};
        
        for (String size : sizes) {
            thumbnails.put(size, generateThumbnailUrl(originalUrl, size));
        }

        return thumbnails;
    }

    /**
     * Validate image dimensions.
     */
    public static boolean isValidDimensions(int width, int height) {
        if (width < 50 || height < 50) {
            log.debug("Image dimensions too small: {}x{}", width, height);
            return false;
        }

        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
            log.debug("Image dimensions too large: {}x{}", width, height);
            return false;
        }

        double aspectRatio = (double) width / height;
        if (aspectRatio < 0.1 || aspectRatio > 10.0) {
            log.debug("Invalid aspect ratio: {} ({}x{})", aspectRatio, width, height);
            return false;
        }

        return true;
    }

    /**
     * Generate alt text for image based on product information.
     */
    public static String generateAltText(String productName, String imageType, int index) {
        if (productName == null || productName.trim().isEmpty()) {
            return "Product image";
        }

        StringBuilder altText = new StringBuilder(productName.trim());

        if (imageType != null && !imageType.trim().isEmpty()) {
            switch (imageType.toLowerCase()) {
                case "main":
                case "primary":
                    altText.append(" - Main product image");
                    break;
                case "gallery":
                    altText.append(" - Gallery image");
                    if (index > 0) {
                        altText.append(" ").append(index + 1);
                    }
                    break;
                case "thumbnail":
                    altText.append(" - Thumbnail");
                    break;
                default:
                    altText.append(" - ").append(imageType);
                    break;
            }
        }

        return altText.toString();
    }

    /**
     * Sanitize image filename for safe storage.
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "image_" + System.currentTimeMillis();
        }

        String sanitized = filename.replaceAll("[/\\\\:*?\"<>|]", "_");
        sanitized = sanitized.replaceAll("_{2,}", "_");
        sanitized = sanitized.replaceAll("^_+|_+$", "");

        if (sanitized.length() > 100) {
            String extension = extractFileExtension(sanitized);
            String baseName = sanitized.substring(0, sanitized.lastIndexOf('.'));
            sanitized = baseName.substring(0, 95) + "." + extension;
        }

        if (sanitized.isEmpty()) {
            sanitized = "image_" + System.currentTimeMillis();
        }

        return sanitized;
    }

    /**
     * Check for suspicious patterns in image URL.
     */
    private static boolean containsSuspiciousPatterns(String url) {
        String lowerUrl = url.toLowerCase();

        String[] suspiciousPatterns = {
            "javascript:", "data:", "vbscript:", "file:", "ftp:",
            "localhost", "127.0.0.1", "0.0.0.0", "::1",
            ".exe", ".bat", ".cmd", ".scr", ".com"
        };

        for (String pattern : suspiciousPatterns) {
            if (lowerUrl.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get recommended image sizes for different use cases.
     */
    public static Map<String, String> getRecommendedSizes() {
        Map<String, String> sizes = new HashMap<>();
        sizes.put("thumbnail", "150x150");
        sizes.put("small", "300x300");
        sizes.put("medium", "600x600");
        sizes.put("large", "1200x1200");
        sizes.put("hero", "1920x1080");
        return sizes;
    }

    /**
     * Calculate optimal image quality based on use case.
     */
    public static int getRecommendedQuality(String useCase) {
        if (useCase == null) {
            return 80;
        }

        switch (useCase.toLowerCase()) {
            case "thumbnail":
                return 70;
            case "gallery":
            case "main":
                return 85;
            case "hero":
            case "banner":
                return 90;
            default:
                return 80;
        }
    }

    /**
     * Check if image URL is from a trusted CDN.
     */
    public static boolean isFromTrustedCdn(String imageUrl) {
        if (imageUrl == null) {
            return false;
        }

        String[] trustedCdns = {
            "cloudinary.com", "amazonaws.com", "cloudfront.net",
            "imgix.net", "fastly.com", "jsdelivr.net", "unpkg.com"
        };

        String lowerUrl = imageUrl.toLowerCase();
        for (String cdn : trustedCdns) {
            if (lowerUrl.contains(cdn)) {
                return true;
            }
        }

        return false;
    }
}
