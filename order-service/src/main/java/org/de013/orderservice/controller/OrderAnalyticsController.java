package org.de013.orderservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.security.UserContextHolder;
import org.de013.orderservice.dto.response.OrderAnalyticsResponse;
import org.de013.orderservice.service.OrderAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/orders/analytics") // Gateway routes /api/v1/orders/** to /orders/**
@RequiredArgsConstructor
@Slf4j
public class OrderAnalyticsController {

    private final OrderAnalyticsService analyticsService;

    @GetMapping("/summary")
    public OrderAnalyticsResponse summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        // Require admin role for analytics
        UserContextHolder.requireAdmin();
        log.info("Admin user {} accessing order analytics summary", UserContextHolder.getCurrentUsername());
        return analyticsService.getAnalytics(start, end);
    }

    @GetMapping("/revenue")
    public OrderAnalyticsResponse revenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        return analyticsService.getAnalytics(start, end);
    }

    @GetMapping("/trends")
    public OrderAnalyticsResponse trends(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        return analyticsService.getAnalytics(start, end);
    }

    @GetMapping("/performance")
    public OrderAnalyticsResponse performance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        return analyticsService.getAnalytics(start, end);
    }
}

