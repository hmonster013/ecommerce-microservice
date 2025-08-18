package org.de013.shoppingcart.entity.enums;

/**
 * Cart Status Enumeration
 * Defines the various states a shopping cart can be in
 */
public enum CartStatus {
    
    /**
     * Cart is active and being used by the user
     */
    ACTIVE("Active", "Cart is currently active and being used"),
    
    /**
     * Cart has been abandoned by the user
     */
    ABANDONED("Abandoned", "Cart has been abandoned by the user"),
    
    /**
     * Cart is being processed for checkout
     */
    CHECKOUT("Checkout", "Cart is being processed for checkout"),
    
    /**
     * Cart has been successfully converted to an order
     */
    CONVERTED("Converted", "Cart has been converted to an order"),
    
    /**
     * Cart has expired due to inactivity
     */
    EXPIRED("Expired", "Cart has expired due to inactivity"),
    
    /**
     * Cart has been saved for later by the user
     */
    SAVED("Saved", "Cart has been saved for later"),
    
    /**
     * Cart has been merged with another cart
     */
    MERGED("Merged", "Cart has been merged with another cart"),
    
    /**
     * Cart has been deleted/removed
     */
    DELETED("Deleted", "Cart has been deleted");

    private final String displayName;
    private final String description;

    CartStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if the cart status allows modifications
     */
    public boolean isModifiable() {
        return this == ACTIVE || this == SAVED;
    }

    /**
     * Check if the cart status is final (cannot be changed)
     */
    public boolean isFinal() {
        return this == CONVERTED || this == DELETED || this == MERGED;
    }

    /**
     * Check if the cart is available for checkout
     */
    public boolean isCheckoutReady() {
        return this == ACTIVE;
    }
}
