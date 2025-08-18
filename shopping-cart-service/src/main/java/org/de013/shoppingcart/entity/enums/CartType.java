package org.de013.shoppingcart.entity.enums;

/**
 * Cart Type Enumeration
 * Defines the type of cart based on user authentication status
 */
public enum CartType {
    
    /**
     * Cart belongs to an authenticated user
     */
    USER("User Cart", "Cart belongs to an authenticated user", 86400), // 24 hours
    
    /**
     * Cart belongs to a guest session
     */
    GUEST("Guest Cart", "Cart belongs to a guest session", 3600), // 1 hour
    
    /**
     * Cart is saved for later by the user
     */
    SAVED("Saved Cart", "Cart saved for later by the user", 2592000), // 30 days
    
    /**
     * Cart is a wishlist
     */
    WISHLIST("Wishlist", "Cart used as a wishlist", 7776000); // 90 days

    private final String displayName;
    private final String description;
    private final int defaultTtlSeconds;

    CartType(String displayName, String description, int defaultTtlSeconds) {
        this.displayName = displayName;
        this.description = description;
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public int getDefaultTtlSeconds() {
        return defaultTtlSeconds;
    }

    /**
     * Check if this cart type requires user authentication
     */
    public boolean requiresAuthentication() {
        return this == USER || this == SAVED || this == WISHLIST;
    }

    /**
     * Check if this cart type supports persistence
     */
    public boolean isPersistent() {
        return this == USER || this == SAVED || this == WISHLIST;
    }

    /**
     * Check if this cart type is temporary
     */
    public boolean isTemporary() {
        return this == GUEST;
    }
}
