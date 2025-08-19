package org.de013.orderservice.service;

import org.de013.orderservice.dto.response.OrderAnalyticsResponse;

import java.time.LocalDateTime;

public interface OrderAnalyticsService {
    OrderAnalyticsResponse getAnalytics(LocalDateTime start, LocalDateTime end);
}

