package org.de013.productcatalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.constant.ApiPaths;
import org.de013.common.controller.BaseController;
import org.de013.productcatalog.dto.inventory.InventoryResponseDto;
import org.de013.productcatalog.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory management API")
public class InventoryController extends BaseController {

    private final InventoryService inventoryService;

    @Operation(summary = "Get product inventory", description = "Retrieve inventory information for a specific product")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Inventory retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product or inventory not found")
    })
    @GetMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY)
    public ResponseEntity<org.de013.common.dto.ApiResponse<InventoryResponseDto>> getProductInventory(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id) {
        
        log.info("Getting inventory for product ID: {}", id);
        
        InventoryResponseDto inventory = inventoryService.getInventoryByProductId(id);
        return ok(inventory);
    }

    // Stock Management Operations
    @Operation(summary = "[ADMIN] Add stock", description = "Add stock to a product")
    @PostMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.ADD)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<InventoryResponseDto>> addStock(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Quantity to add", required = true)
            @RequestParam Integer quantity) {

        log.info("Adding {} stock to product ID: {}", quantity, id);

        InventoryResponseDto inventory = inventoryService.addStock(id, quantity);
        return updated(inventory, String.format("Added %d units to inventory", quantity));
    }

    @Operation(summary = "[ADMIN] Remove stock", description = "Remove stock from a product")
    @PostMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.REMOVE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<InventoryResponseDto>> removeStock(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Quantity to remove", required = true)
            @RequestParam Integer quantity) {

        log.info("Removing {} stock from product ID: {}", quantity, id);

        InventoryResponseDto inventory = inventoryService.removeStock(id, quantity);
        return updated(inventory, String.format("Removed %d units from inventory", quantity));
    }

    @Operation(summary = "[ADMIN] Set stock level", description = "Set exact stock level for a product")
    @PostMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.SET)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<InventoryResponseDto>> setStock(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "New stock quantity", required = true)
            @RequestParam Integer quantity) {

        log.info("Setting stock to {} for product ID: {}", quantity, id);

        InventoryResponseDto inventory = inventoryService.setStock(id, quantity);
        return updated(inventory, String.format("Set inventory to %d units", quantity));
    }

    // Stock Reservation Operations
    @Operation(summary = "[ADMIN] Reserve stock", description = "Reserve stock for an order")
    @PostMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.RESERVE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Boolean>> reserveStock(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Quantity to reserve", required = true)
            @RequestParam Integer quantity) {

        log.info("Reserving {} stock for product ID: {}", quantity, id);

        boolean success = inventoryService.reserveStock(id, quantity);
        String message = success ?
                String.format("Reserved %d units successfully", quantity) :
                "Failed to reserve stock - insufficient quantity";

        return success(success, message);
    }

    @Operation(summary = "[ADMIN] Release reserved stock", description = "Release previously reserved stock")
    @PostMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.RELEASE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Boolean>> releaseReservedStock(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Quantity to release", required = true)
            @RequestParam Integer quantity) {

        log.info("Releasing {} reserved stock for product ID: {}", quantity, id);

        boolean success = inventoryService.releaseReservedStock(id, quantity);
        String message = success ?
                String.format("Released %d reserved units successfully", quantity) :
                "Failed to release reserved stock";

        return success(success, message);
    }

    @Operation(summary = "[ADMIN] Fulfill order", description = "Fulfill an order by reducing stock and reserved quantity")
    @PostMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.FULFILL)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Boolean>> fulfillOrder(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Quantity to fulfill", required = true)
            @RequestParam Integer quantity) {

        log.info("Fulfilling order of {} for product ID: {}", quantity, id);

        boolean success = inventoryService.fulfillOrder(id, quantity);
        String message = success ?
                String.format("Fulfilled order for %d units successfully", quantity) :
                "Failed to fulfill order - insufficient reserved stock";

        return success(success, message);
    }

    @Operation(summary = "Check stock availability", description = "Check if sufficient stock is available for an order")
    @GetMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.CHECK)
    public ResponseEntity<org.de013.common.dto.ApiResponse<Boolean>> checkStockAvailability(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Quantity to check", required = true)
            @RequestParam Integer quantity) {

        log.info("Checking stock availability for product ID: {}, quantity: {}", id, quantity);

        boolean available = inventoryService.getAvailableQuantity(id) >= quantity;
        String message = available ?
                "Stock is available" :
                "Insufficient stock available";

        return success(available, message);
    }
}
