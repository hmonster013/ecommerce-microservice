package org.de013.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.de013.common.constant.ApiPaths;
import org.de013.common.controller.BaseController;
import org.de013.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping(ApiPaths.HEALTH) // Gateway routes /api/v1/orders/** to /orders/**
@Tag(name = "Health Check", description = "Service health check endpoints")
public class HealthController extends BaseController {

    @GetMapping
    @Operation(summary = "Health check", description = "Check if the order service is running")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> healthData = Map.of(
                "status", "UP",
                "service", "order-service",
                "timestamp", LocalDateTime.now(),
                "version", "1.0.0"
        );

        return ok(healthData);
    }
}
