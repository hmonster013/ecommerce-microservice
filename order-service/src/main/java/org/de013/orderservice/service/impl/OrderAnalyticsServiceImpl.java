package org.de013.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.de013.orderservice.dto.response.OrderAnalyticsResponse;
import org.de013.orderservice.repository.custom.OrderAnalyticsRepository;
import org.de013.orderservice.service.OrderAnalyticsService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrderAnalyticsServiceImpl implements OrderAnalyticsService {

    private final OrderAnalyticsRepository analyticsRepository;

    @Override
    public OrderAnalyticsResponse getAnalytics(LocalDateTime start, LocalDateTime end) {
        // Minimal implementation using repository placeholders
        var metrics = analyticsRepository.getOrderMetrics(start, end);
        var revenue = analyticsRepository.getRevenueAnalytics(start, end);
        return OrderAnalyticsResponse.builder()
                .period(OrderAnalyticsResponse.AnalyticsPeriod.builder().startDate(start).endDate(end).build())
                .orderMetrics(OrderAnalyticsResponse.OrderMetrics.builder().totalOrders(metrics != null ? metrics.getTotalOrders() : 0L).build())
                .revenueAnalytics(OrderAnalyticsResponse.RevenueAnalytics.builder().totalRevenue(revenue != null ? org.de013.orderservice.entity.valueobject.Money.of(revenue.getTotalRevenue(), revenue.getCurrency()) : org.de013.orderservice.entity.valueobject.Money.zero("USD")).build())
                .build();
    }
}

