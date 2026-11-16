package org.de013.orderservice.client;

import org.de013.common.dto.ApiResult;
import org.de013.orderservice.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for Product Catalog Service integration
 */
@FeignClient(name = "product-catalog-service", configuration = FeignConfig.class, fallbackFactory = org.de013.orderservice.config.FeignResilienceConfig.RethrowFallbackFactory.class)
public interface ProductCatalogClient {

    /**
     * Deduct inventory for a product
     */
    @PostMapping("/products/{id}/inventory/remove")
    ApiResult<Object> removeStock(
            @PathVariable("id") String id,
            @RequestParam("quantity") Integer quantity
    );
}
