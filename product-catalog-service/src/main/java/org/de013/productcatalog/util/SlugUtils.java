package org.de013.productcatalog.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Utility class for slug generation and manipulation.
 * Provides methods to create URL-friendly slugs from text.
 */
@Slf4j
@UtilityClass
public class SlugUtils {

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");
    private static final Pattern MULTIPLE_HYPHENS = Pattern.compile("-{2,}");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("^-|-$");

    /**
     * Generate a URL-friendly slug from the given text.
     * 
     * @param input the input text
     * @return URL-friendly slug
     */
    public static String generateSlug(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }

        log.debug("Generating slug from: {}", input);

        String slug = input.trim()
                // Convert to lowercase
                .toLowerCase()
                // Normalize unicode characters
                .replace("đ", "d")
                .replace("Đ", "d");

        // Normalize unicode characters (remove accents)
        slug = Normalizer.normalize(slug, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // Replace whitespace with hyphens
        slug = WHITESPACE.matcher(slug).replaceAll("-");

        // Remove non-latin characters (keep letters, numbers, hyphens)
        slug = NON_LATIN.matcher(slug).replaceAll("");

        // Replace multiple consecutive hyphens with single hyphen
        slug = MULTIPLE_HYPHENS.matcher(slug).replaceAll("-");

        // Remove hyphens from beginning and end
        slug = EDGE_HYPHENS.matcher(slug).replaceAll("");

        // Ensure minimum length
        if (slug.length() < 2) {
            slug = "item-" + System.currentTimeMillis() % 10000;
        }

        // Ensure maximum length
        if (slug.length() > 100) {
            slug = slug.substring(0, 97) + "...";
            // Remove trailing hyphen if created by truncation
            slug = slug.replaceAll("-+$", "");
        }

        log.debug("Generated slug: {}", slug);
        return slug;
    }

    /**
     * Generate a unique slug by appending a number if the base slug already exists.
     * 
     * @param baseSlug the base slug
     * @param existingSlugChecker function to check if slug exists
     * @return unique slug
     */
    public static String generateUniqueSlug(String baseSlug, java.util.function.Function<String, Boolean> existingSlugChecker) {
        String slug = generateSlug(baseSlug);
        
        if (!existingSlugChecker.apply(slug)) {
            return slug;
        }

        // Try appending numbers
        for (int i = 1; i <= 999; i++) {
            String candidateSlug = slug + "-" + i;
            if (!existingSlugChecker.apply(candidateSlug)) {
                log.debug("Generated unique slug: {} (attempt {})", candidateSlug, i);
                return candidateSlug;
            }
        }

        // Fallback to timestamp-based slug
        String timestampSlug = slug + "-" + System.currentTimeMillis();
        log.warn("Used timestamp-based slug as fallback: {}", timestampSlug);
        return timestampSlug;
    }

    /**
     * Validate if a string is a valid slug format.
     * 
     * @param slug the slug to validate
     * @return true if valid slug format
     */
    public static boolean isValidSlug(String slug) {
        if (slug == null || slug.trim().isEmpty()) {
            return false;
        }

        // Check basic pattern
        if (!slug.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$")) {
            return false;
        }

        // Check length constraints
        if (slug.length() < 2 || slug.length() > 100) {
            return false;
        }

        // Check for consecutive hyphens
        if (slug.contains("--")) {
            return false;
        }

        return true;
    }

    /**
     * Extract slug from a full URL path.
     * 
     * @param path the URL path
     * @return extracted slug
     */
    public static String extractSlugFromPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "";
        }

        // Remove leading/trailing slashes
        path = path.replaceAll("^/+|/+$", "");

        // Get the last segment
        String[] segments = path.split("/");
        if (segments.length == 0) {
            return "";
        }

        String lastSegment = segments[segments.length - 1];

        // Remove query parameters and fragments
        lastSegment = lastSegment.split("\\?")[0].split("#")[0];

        return isValidSlug(lastSegment) ? lastSegment : "";
    }

    /**
     * Convert slug back to human-readable title.
     *
     * @param slug the slug
     * @return human-readable title
     */
    public static String slugToTitle(String slug) {
        if (slug == null || slug.trim().isEmpty()) {
            return "";
        }

        String[] words = slug.replace("-", " ").split("\\s+");
        StringBuilder title = new StringBuilder();

        for (String word : words) {
            if (word.length() > 0) {
                if (title.length() > 0) {
                    title.append(" ");
                }
                title.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    title.append(word.substring(1).toLowerCase());
                }
            }
        }

        return title.toString();
    }

    /**
     * Check if slug contains only safe characters for URLs.
     * 
     * @param slug the slug to check
     * @return true if slug is URL-safe
     */
    public static boolean isUrlSafe(String slug) {
        if (slug == null) {
            return false;
        }

        // Check for URL-unsafe characters
        return !slug.matches(".*[^a-z0-9\\-].*");
    }

    /**
     * Sanitize slug by removing or replacing unsafe characters.
     * 
     * @param slug the slug to sanitize
     * @return sanitized slug
     */
    public static String sanitizeSlug(String slug) {
        if (slug == null) {
            return "";
        }

        return generateSlug(slug);
    }
}
