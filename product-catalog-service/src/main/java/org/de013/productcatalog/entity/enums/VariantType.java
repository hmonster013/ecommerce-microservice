package org.de013.productcatalog.entity.enums;

import lombok.Getter;

@Getter
public enum VariantType {
    SIZE("Size", "Product size variations (S, M, L, XL, etc.)"),
    COLOR("Color", "Product color variations"),
    MATERIAL("Material", "Product material variations"),
    STYLE("Style", "Product style variations"),
    CAPACITY("Capacity", "Product capacity variations (for storage, memory, etc.)"),
    WEIGHT("Weight", "Product weight variations"),
    DIMENSION("Dimension", "Product dimension variations"),
    FLAVOR("Flavor", "Product flavor variations (for food/beverage)"),
    SCENT("Scent", "Product scent variations (for cosmetics/perfumes)"),
    PATTERN("Pattern", "Product pattern variations"),
    FINISH("Finish", "Product finish variations (matte, glossy, etc.)"),
    CUSTOM("Custom", "Custom variant type");

    private final String displayName;
    private final String description;

    VariantType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public boolean isStandardType() {
        return this != CUSTOM;
    }
}
