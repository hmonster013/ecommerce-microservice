package org.de013.orderservice.client;

import org.de013.common.dto.ApiResponse;
import org.de013.orderservice.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for Product Catalog Service integration
 */
@FeignClient(name = "product-catalog-service", configuration = FeignConfig.class)
public interface ProductCatalogClient {

    /**
     * Deduct inventory for a product
     */
    @PostMapping("/products/{id}/inventory/remove")
    ApiResponse<Object> removeStock(
            @PathVariable("id") Long id,
            @RequestParam("quantity") Integer quantity
    );
}
