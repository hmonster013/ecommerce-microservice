package org.de013.shoppingcart.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.repository.jpa.CartAnalyticsRepository;
import org.de013.shoppingcart.repository.jpa.CartRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Cart Analytics
 * Provides endpoints for cart analytics, metrics, and reporting
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Cart Analytics", description = "APIs for cart analytics and metrics")
public class CartAnalyticsController {

    private final CartAnalyticsRepository analyticsRepository;
    private final CartRepository cartRepository;

    // ==================== CONVERSION ANALYTICS ====================

    @Operation(summary = "Get conversion funnel", description = "Get cart conversion funnel metrics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Conversion funnel retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid date range"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/conversion/funnel")
    public ResponseEntity<Map<String, Object>> getConversionFunnel(
            @Parameter(description = "Start date (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        try {
            log.debug("Getting conversion funnel from {} to {}", startDate, endDate);
            
            if (startDate.isAfter(endDate)) {
                return ResponseEntity.badRequest().build();
            }
            
            List<Object[]> funnelData = analyticsRepository.getCartConversionFunnel(startDate, endDate);
            
            Map<String, Object> funnel = new HashMap<>();
            for (Object[] row : funnelData) {
                String eventType = (String) row[0];
                Long count = (Long) row[1];
                funnel.put(eventType.toLowerCase().replace("_", ""), count);
            }
            
            // Calculate conversion rates
            Long created = (Long) funnel.getOrDefault("cartcreated", 0L);
            Long converted = (Long) funnel.getOrDefault("cartconverted", 0L);
            
            double conversionRate = created > 0 ? (converted.doubleValue() / created.doubleValue()) * 100 : 0.0;
            
            Map<String, Object> result = Map.of(
                "funnel", funnel,
                "conversionRate", conversionRate,
                "period", Map.of("start", startDate, "end", endDate)
            );
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error getting conversion funnel: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get abandonment analytics", description = "Get cart abandonment metrics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Abandonment analytics retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid date range"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/abandonment")
    public ResponseEntity<Map<String, Object>> getAbandonmentAnalytics(
            @Parameter(description = "Start date (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        try {
            log.debug("Getting abandonment analytics from {} to {}", startDate, endDate);
            
            if (startDate.isAfter(endDate)) {
                return ResponseEntity.badRequest().build();
            }
            
            Object[] abandonmentData = analyticsRepository.getCartAbandonmentAnalytics(startDate, endDate);
            
            Map<String, Object> result = Map.of(
                "abandonedCarts", abandonmentData[0],
                "averageCartValue", abandonmentData[1],
                "averageItemCount", abandonmentData[2],
                "period", Map.of("start", startDate, "end", endDate)
            );
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error getting abandonment analytics: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== PRODUCT ANALYTICS ====================

    @Operation(summary = "Get popular products", description = "Get most popular products added to carts")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Popular products retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/products/popular")
    public ResponseEntity<List<Map<String, Object>>> getPopularProducts(
            @Parameter(description = "Start date (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @Parameter(description = "Number of results to return")
            @RequestParam(defaultValue = "10") int limit) {
        
        try {
            log.debug("Getting popular products from {} to {}, limit: {}", startDate, endDate, limit);
            
            if (startDate.isAfter(endDate) || limit <= 0 || limit > 100) {
                return ResponseEntity.badRequest().build();
            }
            
            List<Object[]> popularProducts = analyticsRepository.getMostAddedProducts(
                startDate, endDate, PageRequest.of(0, limit));
            
            List<Map<String, Object>> result = popularProducts.stream()
                    .map(row -> Map.of(
                        "productId", row[0],
                        "timesAdded", row[1],
                        "totalQuantity", row[2]
                    ))
                    .toList();
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error getting popular products: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get product performance", description = "Get comprehensive product performance in carts")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Product performance retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid date range"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/products/performance")
    public ResponseEntity<List<Map<String, Object>>> getProductPerformance(
            @Parameter(description = "Start date (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        try {
            log.debug("Getting product performance from {} to {}", startDate, endDate);
            
            if (startDate.isAfter(endDate)) {
                return ResponseEntity.badRequest().build();
            }
            
            List<Object[]> performance = analyticsRepository.getProductPerformanceInCarts(startDate, endDate);
            
            List<Map<String, Object>> result = performance.stream()
                    .map(row -> Map.of(
                        "productId", row[0],
                        "productSku", row[1],
                        "timesAdded", row[2],
                        "timesRemoved", row[3],
                        "uniqueCarts", row[4],
                        "netAdditions", ((Number) row[2]).longValue() - ((Number) row[3]).longValue()
                    ))
                    .toList();
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error getting product performance: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== USER BEHAVIOR ANALYTICS ====================

    @Operation(summary = "Get user engagement", description = "Get user engagement metrics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User engagement retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/users/engagement")
    public ResponseEntity<List<Map<String, Object>>> getUserEngagement(
            @Parameter(description = "Start date (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @Parameter(description = "Number of results to return")
            @RequestParam(defaultValue = "20") int limit) {
        
        try {
            log.debug("Getting user engagement from {} to {}, limit: {}", startDate, endDate, limit);
            
            if (startDate.isAfter(endDate) || limit <= 0 || limit > 100) {
                return ResponseEntity.badRequest().build();
            }
            
            List<Object[]> engagement = analyticsRepository.getUserEngagementMetrics(
                startDate, endDate, PageRequest.of(0, limit));
            
            List<Map<String, Object>> result = engagement.stream()
                    .map(row -> Map.of(
                        "userId", row[0],
                        "totalEvents", row[1],
                        "uniqueCarts", row[2],
                        "lastActivity", row[3]
                    ))
                    .toList();
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error getting user engagement: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== DEVICE & PLATFORM ANALYTICS ====================

    @Operation(summary = "Get device analytics", description = "Get device type usage analytics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Device analytics retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid date range"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/devices")
    public ResponseEntity<List<Map<String, Object>>> getDeviceAnalytics(
            @Parameter(description = "Start date (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        try {
            log.debug("Getting device analytics from {} to {}", startDate, endDate);
            
            if (startDate.isAfter(endDate)) {
                return ResponseEntity.badRequest().build();
            }
            
            List<Object[]> deviceData = analyticsRepository.getDeviceTypeAnalytics(startDate, endDate);
            
            List<Map<String, Object>> result = deviceData.stream()
                    .map(row -> Map.of(
                        "deviceType", row[0],
                        "totalEvents", row[1],
                        "uniqueUsers", row[2],
                        "uniqueSessions", row[3]
                    ))
                    .toList();
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error getting device analytics: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== PERFORMANCE ANALYTICS ====================

    @Operation(summary = "Get performance metrics", description = "Get cart operation performance metrics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Performance metrics retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid date range"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/performance")
    public ResponseEntity<List<Map<String, Object>>> getPerformanceMetrics(
            @Parameter(description = "Start date (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        try {
            log.debug("Getting performance metrics from {} to {}", startDate, endDate);
            
            if (startDate.isAfter(endDate)) {
                return ResponseEntity.badRequest().build();
            }
            
            List<Object[]> performanceData = analyticsRepository.getProcessingTimeAnalytics(startDate, endDate);
            
            List<Map<String, Object>> result = performanceData.stream()
                    .map(row -> Map.of(
                        "eventType", row[0],
                        "averageProcessingTime", row[1],
                        "totalEvents", row[2]
                    ))
                    .toList();
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error getting performance metrics: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== SUMMARY DASHBOARD ====================

    @Operation(summary = "Get analytics dashboard", description = "Get comprehensive analytics dashboard data")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Dashboard data retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid date range"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getAnalyticsDashboard(
            @Parameter(description = "Start date (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        try {
            log.debug("Getting analytics dashboard from {} to {}", startDate, endDate);
            
            if (startDate.isAfter(endDate)) {
                return ResponseEntity.badRequest().build();
            }
            
            // Get various metrics for dashboard
            Object[] conversionMetrics = cartRepository.getCartConversionMetrics(startDate, endDate);
            Object[] abandonmentData = analyticsRepository.getCartAbandonmentAnalytics(startDate, endDate);
            List<Object[]> topProducts = analyticsRepository.getMostAddedProducts(
                startDate, endDate, PageRequest.of(0, 5));
            
            Map<String, Object> dashboard = Map.of(
                "period", Map.of("start", startDate, "end", endDate),
                "conversion", Map.of(
                    "activeCarts", conversionMetrics[0],
                    "convertedCarts", conversionMetrics[1],
                    "abandonedCarts", conversionMetrics[2],
                    "averageOrderValue", conversionMetrics[3]
                ),
                "abandonment", Map.of(
                    "count", abandonmentData[0],
                    "averageValue", abandonmentData[1],
                    "averageItems", abandonmentData[2]
                ),
                "topProducts", topProducts.stream()
                    .map(row -> Map.of(
                        "productId", row[0],
                        "timesAdded", row[1],
                        "totalQuantity", row[2]
                    ))
                    .toList()
            );
            
            return ResponseEntity.ok(dashboard);
            
        } catch (Exception e) {
            log.error("Error getting analytics dashboard: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
