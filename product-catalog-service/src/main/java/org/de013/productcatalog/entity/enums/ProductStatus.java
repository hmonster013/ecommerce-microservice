package org.de013.productcatalog.entity.enums;

import lombok.Getter;

@Getter
public enum ProductStatus {
    ACTIVE("Active", "Product is available for sale"),
    INACTIVE("Inactive", "Product is temporarily unavailable"),
    DISCONTINUED("Discontinued", "Product is no longer available"),
    OUT_OF_STOCK("Out of Stock", "Product is temporarily out of stock");

    private final String displayName;
    private final String description;

    ProductStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public boolean isAvailable() {
        return this == ACTIVE;
    }

    public boolean canBePurchased() {
        return this == ACTIVE;
    }
}
