package org.de013.orderservice.controller;

import lombok.RequiredArgsConstructor;
import org.de013.orderservice.dto.response.OrderAnalyticsResponse;
import org.de013.orderservice.service.OrderAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/orders/analytics")
@RequiredArgsConstructor
public class OrderAnalyticsController {

    private final OrderAnalyticsService analyticsService;

    @GetMapping("/summary")
    public OrderAnalyticsResponse summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
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

