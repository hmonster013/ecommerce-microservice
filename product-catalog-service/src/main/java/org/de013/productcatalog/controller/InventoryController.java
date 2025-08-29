package org.de013.productcatalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.constant.ApiPaths;
import org.de013.common.dto.ApiResponse;
import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.inventory.InventoryResponseDto;
import org.de013.productcatalog.dto.inventory.InventoryUpdateDto;
import org.de013.productcatalog.dto.inventory.StockAlertDto;
import org.de013.productcatalog.service.InventoryService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("") // Gateway routes /api/v1/products/** to /products/** - inventory is under products
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory management API")
public class InventoryController {

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
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(inventory));
    }

    @Operation(summary = "Update product inventory", description = "Update inventory information for a specific product (Admin only)")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Inventory updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid inventory data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product or inventory not found")
    })
    @PutMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<InventoryResponseDto>> updateProductInventory(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id,
            
            @Parameter(description = "Inventory update data", required = true)
            @Valid @RequestBody InventoryUpdateDto updateDto) {
        
        log.info("Updating inventory for product ID: {}", id);
        
        InventoryResponseDto inventory = inventoryService.updateInventory(id, updateDto);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(inventory, "Inventory updated successfully"));
    }

    @Operation(summary = "Get low stock products", description = "Retrieve products with low stock levels (Admin only)")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Low stock products retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping(ApiPaths.INVENTORY + ApiPaths.LOW_STOCK)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<PageResponse<InventoryResponseDto>>> getLowStockProducts(
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20, sort = "quantity", direction = Sort.Direction.ASC) Pageable pageable) {
        
        log.info("Getting low stock products");
        
        PageResponse<InventoryResponseDto> lowStockProducts = inventoryService.getLowStockInventories(pageable);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(lowStockProducts));
    }

    @Operation(summary = "Get out of stock products", description = "Retrieve products that are out of stock (Admin only)")
    @GetMapping(ApiPaths.INVENTORY + ApiPaths.OUT_OF_STOCK)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<PageResponse<InventoryResponseDto>>> getOutOfStockProducts(
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        log.info("Getting out of stock products");
        
        PageResponse<InventoryResponseDto> outOfStockProducts = inventoryService.getOutOfStockInventories(pageable);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(outOfStockProducts));
    }

    @Operation(summary = "Get stock alerts", description = "Retrieve all stock alerts (Admin only)")
    @GetMapping(ApiPaths.INVENTORY + ApiPaths.ALERTS)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<StockAlertDto>>> getStockAlerts() {
        log.info("Getting stock alerts");
        
        List<StockAlertDto> alerts = inventoryService.getStockAlerts();
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(alerts));
    }

    @Operation(summary = "Get low stock alerts", description = "Retrieve low stock alerts (Admin only)")
    @GetMapping(ApiPaths.INVENTORY + ApiPaths.ALERTS + ApiPaths.LOW_STOCK)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<StockAlertDto>>> getLowStockAlerts() {
        log.info("Getting low stock alerts");
        
        List<StockAlertDto> alerts = inventoryService.getLowStockAlerts();
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(alerts));
    }

    @Operation(summary = "Get reorder alerts", description = "Retrieve reorder alerts (Admin only)")
    @GetMapping(ApiPaths.INVENTORY + ApiPaths.ALERTS + ApiPaths.REORDER)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<StockAlertDto>>> getReorderAlerts() {
        log.info("Getting reorder alerts");
        
        List<StockAlertDto> alerts = inventoryService.getReorderAlerts();
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(alerts));
    }

    // Stock Management Operations
    @Operation(summary = "Add stock", description = "Add stock to a product (Admin only)")
    @PostMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.ADD)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<InventoryResponseDto>> addStock(
            @PathVariable Long id,
            @Parameter(description = "Quantity to add", required = true)
            @RequestParam Integer quantity) {
        
        log.info("Adding {} stock to product ID: {}", quantity, id);
        
        InventoryResponseDto inventory = inventoryService.addStock(id, quantity);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(inventory, 
                String.format("Added %d units to inventory", quantity)));
    }

    @Operation(summary = "Remove stock", description = "Remove stock from a product (Admin only)")
    @PostMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.REMOVE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<InventoryResponseDto>> removeStock(
            @PathVariable Long id,
            @Parameter(description = "Quantity to remove", required = true)
            @RequestParam Integer quantity) {
        
        log.info("Removing {} stock from product ID: {}", quantity, id);
        
        InventoryResponseDto inventory = inventoryService.removeStock(id, quantity);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(inventory, 
                String.format("Removed %d units from inventory", quantity)));
    }

    @Operation(summary = "Set stock level", description = "Set exact stock level for a product (Admin only)")
    @PostMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.SET)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<InventoryResponseDto>> setStock(
            @PathVariable Long id,
            @Parameter(description = "New stock quantity", required = true)
            @RequestParam Integer quantity) {

        log.info("Setting stock to {} for product ID: {}", quantity, id);

        InventoryResponseDto inventory = inventoryService.setStock(id, quantity);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(inventory,
                String.format("Set inventory to %d units", quantity)));
    }

    @Operation(summary = "Adjust stock", description = "Adjust stock by a positive or negative amount (Admin only)")
    @PostMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.ADJUST)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<InventoryResponseDto>> adjustStock(
            @PathVariable Long id,
            @Parameter(description = "Adjustment amount (positive or negative)", required = true)
            @RequestParam Integer adjustment) {

        log.info("Adjusting stock by {} for product ID: {}", adjustment, id);

        InventoryResponseDto inventory = inventoryService.adjustStock(id, adjustment);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(inventory,
                String.format("Adjusted inventory by %d units", adjustment)));
    }

    // Stock Reservation Operations
    @Operation(summary = "Reserve stock", description = "Reserve stock for an order (Admin only)")
    @PostMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.RESERVE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Boolean>> reserveStock(
            @PathVariable Long id,
            @RequestParam Integer quantity) {

        log.info("Reserving {} stock for product ID: {}", quantity, id);

        boolean success = inventoryService.reserveStock(id, quantity);
        String message = success ?
                String.format("Reserved %d units successfully", quantity) :
                "Failed to reserve stock - insufficient quantity";

        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(success, message));
    }

    @Operation(summary = "Release reserved stock", description = "Release previously reserved stock (Admin only)")
    @PostMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.RELEASE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Boolean>> releaseReservedStock(
            @PathVariable Long id,
            @RequestParam Integer quantity) {

        log.info("Releasing {} reserved stock for product ID: {}", quantity, id);

        boolean success = inventoryService.releaseReservedStock(id, quantity);
        String message = success ?
                String.format("Released %d reserved units successfully", quantity) :
                "Failed to release reserved stock";

        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(success, message));
    }

    @Operation(summary = "Fulfill order", description = "Fulfill an order by reducing stock and reserved quantity (Admin only)")
    @PostMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.FULFILL)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Boolean>> fulfillOrder(
            @PathVariable Long id,
            @RequestParam Integer quantity) {

        log.info("Fulfilling order of {} for product ID: {}", quantity, id);

        boolean success = inventoryService.fulfillOrder(id, quantity);
        String message = success ?
                String.format("Fulfilled order for %d units successfully", quantity) :
                "Failed to fulfill order - insufficient reserved stock";

        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(success, message));
    }

    // Inventory Statistics
    @Operation(summary = "Get inventory statistics", description = "Get overall inventory statistics (Admin only)")
    @GetMapping(ApiPaths.INVENTORY + ApiPaths.STATS)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Object>> getInventoryStatistics() {
        log.info("Getting inventory statistics");

        Object stats = new Object() {
            public final long totalProducts = inventoryService.getTotalInventoryCount();
            public final long inStockProducts = inventoryService.getInStockCount();
            public final long outOfStockProducts = inventoryService.getOutOfStockCount();
            public final long lowStockProducts = inventoryService.getLowStockCount();
            public final long needsReorderProducts = inventoryService.getNeedsReorderCount();
            public final Long totalQuantity = inventoryService.getTotalQuantity();
            public final Long totalReservedQuantity = inventoryService.getTotalReservedQuantity();
            public final Long totalAvailableQuantity = inventoryService.getTotalAvailableQuantity();
        };

        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(stats));
    }

    @Operation(summary = "Check stock availability", description = "Check if sufficient stock is available for an order")
    @GetMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.INVENTORY + ApiPaths.CHECK)
    public ResponseEntity<org.de013.common.dto.ApiResponse<Boolean>> checkStockAvailability(
            @PathVariable Long id,
            @RequestParam Integer quantity) {

        log.info("Checking stock availability for product ID: {}, quantity: {}", id, quantity);

        boolean available = inventoryService.getAvailableQuantity(id) >= quantity;
        String message = available ?
                "Stock is available" :
                "Insufficient stock available";

        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(available, message));
    }
}
