package org.de013.productcatalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.constant.ApiPaths;
import org.de013.common.controller.BaseController;
import org.de013.common.dto.VariantInventoryDto;
import org.de013.productcatalog.service.VariantInventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("")
@RequiredArgsConstructor
@Tag(name = "Variant Inventory", description = "Product variant inventory management API")
public class VariantInventoryController extends BaseController {

    private final VariantInventoryService variantInventoryService;

    @Operation(summary = "Get variant inventory", description = "Retrieve inventory information for a specific variant")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Variant inventory retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Variant or inventory not found")
    })
    @GetMapping(ApiPaths.VARIANTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY)
    public ResponseEntity<org.de013.common.dto.ApiResponse<VariantInventoryDto>> getVariantInventory(
            @Parameter(description = "Variant ID", required = true)
            @PathVariable Long id) {
        
        log.info("Getting inventory for variant ID: {}", id);
        
        VariantInventoryDto inventory = variantInventoryService.getVariantInventoryByVariantId(id);
        return ok(inventory);
    }

    @Operation(summary = "Get all variant inventories for product", description = "Retrieve inventory information for all variants of a product")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Variant inventories retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.VARIANTS + ApiPaths.INVENTORY)
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<VariantInventoryDto>>> getProductVariantInventories(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id) {
        
        log.info("Getting variant inventories for product ID: {}", id);
        
        List<VariantInventoryDto> inventories = variantInventoryService.getVariantInventoriesByProductId(id);
        return ok(inventories);
    }

    // Stock Management Operations
    @Operation(summary = "[ADMIN] Add stock to variant", description = "Add stock to a specific variant")
    @PostMapping(ApiPaths.VARIANTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.ADD)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<VariantInventoryDto>> addStock(
            @Parameter(description = "Variant ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Quantity to add", required = true)
            @RequestParam Integer quantity) {

        log.info("Adding {} stock to variant ID: {}", quantity, id);

        VariantInventoryDto inventory = variantInventoryService.addStock(id, quantity);
        return updated(inventory, String.format("Added %d units to variant inventory", quantity));
    }

    @Operation(summary = "[ADMIN] Remove stock from variant", description = "Remove stock from a specific variant")
    @PostMapping(ApiPaths.VARIANTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.REMOVE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<VariantInventoryDto>> removeStock(
            @Parameter(description = "Variant ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Quantity to remove", required = true)
            @RequestParam Integer quantity) {

        log.info("Removing {} stock from variant ID: {}", quantity, id);

        VariantInventoryDto inventory = variantInventoryService.removeStock(id, quantity);
        return updated(inventory, String.format("Removed %d units from variant inventory", quantity));
    }

    @Operation(summary = "[ADMIN] Set stock level for variant", description = "Set exact stock level for a specific variant")
    @PostMapping(ApiPaths.VARIANTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.SET)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<VariantInventoryDto>> setStock(
            @Parameter(description = "Variant ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "New stock quantity", required = true)
            @RequestParam Integer quantity) {

        log.info("Setting stock to {} for variant ID: {}", quantity, id);

        VariantInventoryDto inventory = variantInventoryService.setStock(id, quantity);
        return updated(inventory, String.format("Set variant inventory to %d units", quantity));
    }

    // Stock Reservation Operations
    @Operation(summary = "[ADMIN] Reserve variant stock", description = "Reserve stock for a specific variant")
    @PostMapping(ApiPaths.VARIANTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.RESERVE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Boolean>> reserveStock(
            @Parameter(description = "Variant ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Quantity to reserve", required = true)
            @RequestParam Integer quantity) {

        log.info("Reserving {} stock for variant ID: {}", quantity, id);

        boolean success = variantInventoryService.reserveStock(id, quantity);
        String message = success ?
                String.format("Reserved %d units successfully", quantity) :
                "Failed to reserve stock - insufficient quantity";

        return success(success, message);
    }

    @Operation(summary = "[ADMIN] Release reserved variant stock", description = "Release previously reserved stock for a specific variant")
    @PostMapping(ApiPaths.VARIANTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.RELEASE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Boolean>> releaseReservedStock(
            @Parameter(description = "Variant ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Quantity to release", required = true)
            @RequestParam Integer quantity) {

        log.info("Releasing {} reserved stock for variant ID: {}", quantity, id);

        boolean success = variantInventoryService.releaseReservedStock(id, quantity);
        String message = success ?
                String.format("Released %d reserved units successfully", quantity) :
                "Failed to release reserved stock";

        return success(success, message);
    }

    @Operation(summary = "[ADMIN] Fulfill order for variant", description = "Fulfill an order by reducing stock and reserved quantity for a specific variant")
    @PostMapping(ApiPaths.VARIANTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.FULFILL)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Boolean>> fulfillOrder(
            @Parameter(description = "Variant ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Quantity to fulfill", required = true)
            @RequestParam Integer quantity) {

        log.info("Fulfilling order of {} for variant ID: {}", quantity, id);

        boolean success = variantInventoryService.fulfillOrder(id, quantity);
        String message = success ?
                String.format("Fulfilled order for %d units successfully", quantity) :
                "Failed to fulfill order - insufficient reserved stock";

        return success(success, message);
    }

    @Operation(summary = "Check variant stock availability", description = "Check if sufficient stock is available for a specific variant")
    @GetMapping(ApiPaths.VARIANTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.CHECK)
    public ResponseEntity<org.de013.common.dto.ApiResponse<Boolean>> checkStockAvailability(
            @Parameter(description = "Variant ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Quantity to check", required = true)
            @RequestParam Integer quantity) {

        log.info("Checking stock availability for variant ID: {}, quantity: {}", id, quantity);

        boolean available = variantInventoryService.getAvailableQuantity(id) >= quantity;
        String message = available ?
                "Stock is available" :
                "Insufficient stock available";

        return success(available, message);
    }

    @Operation(summary = "Get available quantity for variant", description = "Get the available quantity for a specific variant")
    @GetMapping(ApiPaths.VARIANTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + "/available")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Integer>> getAvailableQuantity(
            @Parameter(description = "Variant ID", required = true)
            @PathVariable Long id) {

        log.info("Getting available quantity for variant ID: {}", id);

        Integer availableQuantity = variantInventoryService.getAvailableQuantity(id);
        return success(availableQuantity, String.format("Available quantity: %d units", availableQuantity));
    }

    @Operation(summary = "Check if variant is in stock", description = "Check if a specific variant is in stock")
    @GetMapping(ApiPaths.VARIANTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + "/in-stock")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Boolean>> isVariantInStock(
            @Parameter(description = "Variant ID", required = true)
            @PathVariable Long id) {

        log.info("Checking if variant ID: {} is in stock", id);

        boolean inStock = variantInventoryService.isVariantInStock(id);
        String message = inStock ? "Variant is in stock" : "Variant is out of stock";

        return success(inStock, message);
    }
}
