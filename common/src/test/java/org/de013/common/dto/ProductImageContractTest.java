package org.de013.common.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductImageContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsCatalogImageUrlIntoTheSharedProductImage() throws Exception {
        ProductDetailDto product = mapper.readValue("""
                {"images":[{"id":1,"url":"https://example.test/main.jpg","imageType":"MAIN"}]}
                """, ProductDetailDto.class);

        assertEquals("https://example.test/main.jpg", product.getImages().get(0).getImageUrl());
    }

    @Test
    void preservesTheExistingSharedImageUrlProperty() throws Exception {
        ProductDetailDto.ProductImageDto image = mapper.readValue("""
                {"imageUrl":"https://example.test/main.jpg"}
                """, ProductDetailDto.ProductImageDto.class);

        assertEquals("https://example.test/main.jpg", mapper.readTree(mapper.writeValueAsString(image))
                .get("imageUrl").asText());
    }
}
