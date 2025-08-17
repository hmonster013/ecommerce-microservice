package org.de013.productcatalog.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class for search functionality.
 * Provides methods for query processing, text normalization, and search optimization.
 */
@Slf4j
@UtilityClass
public class SearchUtils {

    private static final Pattern SPECIAL_CHARS = Pattern.compile("[^\\p{L}\\p{N}\\s]");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");
    private static final Set<String> STOP_WORDS = Set.of(
        "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "has", "he", "in", "is", "it",
        "its", "of", "on", "that", "the", "to", "was", "will", "with", "the", "this", "but", "they",
        "have", "had", "what", "said", "each", "which", "their", "time", "if", "up", "out", "many",
        "then", "them", "these", "so", "some", "her", "would", "make", "like", "into", "him", "two",
        "more", "go", "no", "way", "could", "my", "than", "first", "been", "call", "who", "oil", "sit",
        "now", "find", "down", "day", "did", "get", "come", "made", "may", "part"
    );

    /**
     * Normalize search query for better matching.
     * 
     * @param query the search query
     * @return normalized query
     */
    public static String normalizeQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "";
        }

        log.debug("Normalizing query: {}", query);

        String normalized = query.trim()
                // Convert to lowercase
                .toLowerCase()
                // Handle Vietnamese characters
                .replace("đ", "d")
                .replace("Đ", "d");

        // Normalize unicode characters (remove accents)
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // Remove special characters except spaces
        normalized = SPECIAL_CHARS.matcher(normalized).replaceAll(" ");

        // Replace multiple spaces with single space
        normalized = MULTIPLE_SPACES.matcher(normalized).replaceAll(" ");

        // Trim again
        normalized = normalized.trim();

        log.debug("Normalized query: {}", normalized);
        return normalized;
    }

    /**
     * Extract search terms from query, removing stop words.
     * 
     * @param query the search query
     * @return list of search terms
     */
    public static List<String> extractSearchTerms(String query) {
        String normalized = normalizeQuery(query);
        
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(normalized.split("\\s+"))
                .filter(term -> term.length() > 1) // Remove single characters
                .filter(term -> !STOP_WORDS.contains(term)) // Remove stop words
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Generate search keywords from text for indexing.
     * 
     * @param text the text to process
     * @return set of keywords
     */
    public static Set<String> generateKeywords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> keywords = new HashSet<>();
        String normalized = normalizeQuery(text);

        // Add full normalized text
        if (!normalized.isEmpty()) {
            keywords.add(normalized);
        }

        // Add individual terms
        List<String> terms = extractSearchTerms(text);
        keywords.addAll(terms);

        // Add partial matches (prefixes) for longer terms
        for (String term : terms) {
            if (term.length() > 3) {
                for (int i = 3; i <= term.length(); i++) {
                    keywords.add(term.substring(0, i));
                }
            }
        }

        log.debug("Generated {} keywords from text: {}", keywords.size(), text);
        return keywords;
    }

    /**
     * Calculate search relevance score between query and text.
     * 
     * @param query the search query
     * @param text the text to match against
     * @return relevance score (0.0 to 1.0)
     */
    public static double calculateRelevanceScore(String query, String text) {
        if (query == null || text == null || query.trim().isEmpty() || text.trim().isEmpty()) {
            return 0.0;
        }

        String normalizedQuery = normalizeQuery(query);
        String normalizedText = normalizeQuery(text);

        // Exact match gets highest score
        if (normalizedText.equals(normalizedQuery)) {
            return 1.0;
        }

        // Contains full query gets high score
        if (normalizedText.contains(normalizedQuery)) {
            return 0.8;
        }

        // Calculate term-based score
        List<String> queryTerms = extractSearchTerms(query);
        List<String> textTerms = extractSearchTerms(text);

        if (queryTerms.isEmpty() || textTerms.isEmpty()) {
            return 0.0;
        }

        // Count matching terms
        long matchingTerms = queryTerms.stream()
                .mapToLong(queryTerm -> textTerms.stream()
                        .mapToLong(textTerm -> textTerm.contains(queryTerm) ? 1 : 0)
                        .sum())
                .sum();

        double termScore = (double) matchingTerms / queryTerms.size();

        // Boost score for partial matches
        double partialScore = queryTerms.stream()
                .mapToDouble(queryTerm -> textTerms.stream()
                        .mapToDouble(textTerm -> calculatePartialMatch(queryTerm, textTerm))
                        .max()
                        .orElse(0.0))
                .average()
                .orElse(0.0);

        return Math.max(termScore, partialScore * 0.6);
    }

    /**
     * Calculate partial match score between two terms.
     * 
     * @param term1 first term
     * @param term2 second term
     * @return partial match score (0.0 to 1.0)
     */
    private static double calculatePartialMatch(String term1, String term2) {
        if (term1.equals(term2)) {
            return 1.0;
        }

        if (term1.contains(term2) || term2.contains(term1)) {
            return 0.8;
        }

        // Calculate Levenshtein distance for fuzzy matching
        int distance = calculateLevenshteinDistance(term1, term2);
        int maxLength = Math.max(term1.length(), term2.length());

        if (maxLength == 0) {
            return 0.0;
        }

        double similarity = 1.0 - (double) distance / maxLength;
        return similarity > 0.7 ? similarity * 0.5 : 0.0; // Only consider high similarity
    }

    /**
     * Calculate Levenshtein distance between two strings.
     * 
     * @param s1 first string
     * @param s2 second string
     * @return Levenshtein distance
     */
    private static int calculateLevenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]);
                }
            }
        }

        return dp[s1.length()][s2.length()];
    }

    /**
     * Generate search suggestions based on query.
     * 
     * @param query the partial query
     * @param candidates list of candidate strings
     * @param maxSuggestions maximum number of suggestions
     * @return list of suggestions
     */
    public static List<String> generateSuggestions(String query, List<String> candidates, int maxSuggestions) {
        if (query == null || query.trim().isEmpty() || candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        String normalizedQuery = normalizeQuery(query);

        return candidates.stream()
                .filter(Objects::nonNull)
                .map(candidate -> new AbstractMap.SimpleEntry<>(candidate, calculateRelevanceScore(normalizedQuery, candidate)))
                .filter(entry -> entry.getValue() > 0.3) // Minimum relevance threshold
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(maxSuggestions)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Check if query is likely a product code or SKU.
     * 
     * @param query the search query
     * @return true if query looks like a product code
     */
    public static boolean isProductCode(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }

        String normalized = query.trim().toUpperCase();

        // Check for SKU pattern (letters followed by numbers)
        if (normalized.matches("^[A-Z]{2,4}\\d{3,6}$")) {
            return true;
        }

        // Check for barcode pattern (all numbers, 8-14 digits)
        if (normalized.matches("^\\d{8,14}$")) {
            return true;
        }

        // Check for model number pattern
        if (normalized.matches("^[A-Z0-9]{3,}-[A-Z0-9]{2,}$")) {
            return true;
        }

        return false;
    }

    /**
     * Clean and validate search query.
     * 
     * @param query the raw query
     * @param maxLength maximum allowed length
     * @return cleaned query or empty string if invalid
     */
    public static String cleanQuery(String query, int maxLength) {
        if (query == null) {
            return "";
        }

        String cleaned = query.trim();

        // Remove excessive whitespace
        cleaned = MULTIPLE_SPACES.matcher(cleaned).replaceAll(" ");

        // Truncate if too long
        if (cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength).trim();
        }

        // Remove if too short
        if (cleaned.length() < 2) {
            return "";
        }

        return cleaned;
    }

    /**
     * Extract brand names from search query.
     * 
     * @param query the search query
     * @param knownBrands list of known brand names
     * @return list of detected brands
     */
    public static List<String> extractBrands(String query, List<String> knownBrands) {
        if (query == null || knownBrands == null) {
            return Collections.emptyList();
        }

        String normalizedQuery = normalizeQuery(query);

        return knownBrands.stream()
                .filter(brand -> normalizedQuery.contains(normalizeQuery(brand)))
                .collect(Collectors.toList());
    }
}
