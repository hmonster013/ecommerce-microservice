package org.de013.productcatalog.entity.enums;

import lombok.Getter;

@Getter
public enum ImageType {
    MAIN("Main", "Primary product image displayed in listings"),
    GALLERY("Gallery", "Additional product images for gallery view"),
    THUMBNAIL("Thumbnail", "Small image for quick preview"),
    VARIANT("Variant", "Image specific to a product variant"),
    DETAIL("Detail", "Close-up detail images"),
    LIFESTYLE("Lifestyle", "Product in use or lifestyle context"),
    COMPARISON("Comparison", "Images for size or feature comparison"),
    PACKAGING("Packaging", "Product packaging images"),
    INSTRUCTION("Instruction", "Instruction or manual images"),
    WARRANTY("Warranty", "Warranty or certificate images");

    private final String displayName;
    private final String description;

    ImageType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public boolean isPrimary() {
        return this == MAIN;
    }

    public boolean isDisplayInGallery() {
        return this == MAIN || this == GALLERY || this == DETAIL || this == LIFESTYLE;
    }
}
