package org.de013.orderservice.dto.response;

import lombok.*;
import org.de013.orderservice.entity.valueobject.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Order Analytics Response DTO
 * 
 * Response object containing comprehensive order metrics and statistics.
 * Used for business intelligence, reporting, and dashboard displays.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderAnalyticsResponse {
    
    /**
     * Analytics period information
     */
    private AnalyticsPeriod period;
    
    /**
     * Overall order metrics
     */
    private OrderMetrics orderMetrics;
    
    /**
     * Revenue analytics
     */
    private RevenueAnalytics revenueAnalytics;
    
    /**
     * Customer analytics
     */
    private CustomerAnalytics customerAnalytics;
    
    /**
     * Product analytics
     */
    private ProductAnalytics productAnalytics;
    
    /**
     * Geographic analytics
     */
    private GeographicAnalytics geographicAnalytics;
    
    /**
     * Performance metrics
     */
    private PerformanceMetrics performanceMetrics;
    
    /**
     * Trend analysis
     */
    private TrendAnalysis trendAnalysis;
    
    /**
     * Top performers
     */
    private TopPerformers topPerformers;
    
    /**
     * Analytics Period
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AnalyticsPeriod {
        
        /**
         * Period start date
         */
        private LocalDateTime startDate;
        
        /**
         * Period end date
         */
        private LocalDateTime endDate;
        
        /**
         * Period type (DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY)
         */
        private String periodType;
        
        /**
         * Period description
         */
        private String description;
        
        /**
         * Comparison period start date
         */
        private LocalDateTime comparisonStartDate;
        
        /**
         * Comparison period end date
         */
        private LocalDateTime comparisonEndDate;
    }
    
    /**
     * Order Metrics
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderMetrics {
        
        /**
         * Total number of orders
         */
        private Long totalOrders;
        
        /**
         * Orders by status
         */
        private Map<String, Long> ordersByStatus;
        
        /**
         * Orders by type
         */
        private Map<String, Long> ordersByType;
        
        /**
         * Orders by source
         */
        private Map<String, Long> ordersBySource;
        
        /**
         * Average order processing time (hours)
         */
        private Double averageProcessingTime;
        
        /**
         * Average delivery time (hours)
         */
        private Double averageDeliveryTime;
        
        /**
         * Order fulfillment rate (%)
         */
        private Double fulfillmentRate;
        
        /**
         * Order cancellation rate (%)
         */
        private Double cancellationRate;
        
        /**
         * Return rate (%)
         */
        private Double returnRate;
        
        /**
         * On-time delivery rate (%)
         */
        private Double onTimeDeliveryRate;
        
        /**
         * Comparison with previous period
         */
        private MetricsComparison comparison;
    }
    
    /**
     * Revenue Analytics
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RevenueAnalytics {
        
        /**
         * Total revenue
         */
        private Money totalRevenue;
        
        /**
         * Average order value
         */
        private Money averageOrderValue;
        
        /**
         * Total tax collected
         */
        private Money totalTax;
        
        /**
         * Total shipping revenue
         */
        private Money totalShippingRevenue;
        
        /**
         * Total discounts given
         */
        private Money totalDiscounts;
        
        /**
         * Total refunds processed
         */
        private Money totalRefunds;
        
        /**
         * Net revenue (after refunds)
         */
        private Money netRevenue;
        
        /**
         * Revenue by currency
         */
        private Map<String, Money> revenueByCurrency;
        
        /**
         * Revenue by order type
         */
        private Map<String, Money> revenueByOrderType;
        
        /**
         * Daily revenue breakdown
         */
        private List<DailyRevenue> dailyRevenue;
        
        /**
         * Comparison with previous period
         */
        private RevenueComparison comparison;
    }
    
    /**
     * Customer Analytics
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CustomerAnalytics {
        
        /**
         * Total unique customers
         */
        private Long totalCustomers;
        
        /**
         * New customers
         */
        private Long newCustomers;
        
        /**
         * Returning customers
         */
        private Long returningCustomers;
        
        /**
         * Customer retention rate (%)
         */
        private Double retentionRate;
        
        /**
         * Average orders per customer
         */
        private Double averageOrdersPerCustomer;
        
        /**
         * Customer lifetime value
         */
        private Money customerLifetimeValue;
        
        /**
         * Top customers by order count
         */
        private List<CustomerMetric> topCustomersByOrders;
        
        /**
         * Top customers by revenue
         */
        private List<CustomerMetric> topCustomersByRevenue;
    }
    
    /**
     * Product Analytics
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductAnalytics {
        
        /**
         * Total unique products ordered
         */
        private Long totalProducts;
        
        /**
         * Total items sold
         */
        private Long totalItemsSold;
        
        /**
         * Average items per order
         */
        private Double averageItemsPerOrder;
        
        /**
         * Top selling products
         */
        private List<ProductMetric> topSellingProducts;
        
        /**
         * Top revenue generating products
         */
        private List<ProductMetric> topRevenueProducts;
        
        /**
         * Product categories performance
         */
        private List<CategoryMetric> categoryPerformance;
        
        /**
         * Product return rates
         */
        private List<ProductReturnMetric> productReturnRates;
    }
    
    /**
     * Geographic Analytics
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GeographicAnalytics {
        
        /**
         * Orders by country
         */
        private Map<String, Long> ordersByCountry;
        
        /**
         * Orders by state/province
         */
        private Map<String, Long> ordersByState;
        
        /**
         * Orders by city
         */
        private Map<String, Long> ordersByCity;
        
        /**
         * Revenue by country
         */
        private Map<String, Money> revenueByCountry;
        
        /**
         * International vs domestic orders
         */
        private Map<String, Long> domesticVsInternational;
        
        /**
         * Average delivery time by region
         */
        private Map<String, Double> deliveryTimeByRegion;
    }
    
    /**
     * Performance Metrics
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PerformanceMetrics {
        
        /**
         * Order processing efficiency (%)
         */
        private Double processingEfficiency;
        
        /**
         * Shipping accuracy rate (%)
         */
        private Double shippingAccuracy;
        
        /**
         * Customer satisfaction score
         */
        private Double customerSatisfaction;
        
        /**
         * Payment success rate (%)
         */
        private Double paymentSuccessRate;
        
        /**
         * Inventory turnover rate
         */
        private Double inventoryTurnover;
        
        /**
         * Cost per order
         */
        private Money costPerOrder;
        
        /**
         * Profit margin (%)
         */
        private Double profitMargin;
    }
    
    /**
     * Trend Analysis
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TrendAnalysis {
        
        /**
         * Order volume trend (INCREASING, DECREASING, STABLE)
         */
        private String orderVolumeTrend;
        
        /**
         * Revenue trend
         */
        private String revenueTrend;
        
        /**
         * Average order value trend
         */
        private String aovTrend;
        
        /**
         * Customer acquisition trend
         */
        private String customerAcquisitionTrend;
        
        /**
         * Seasonal patterns
         */
        private List<SeasonalPattern> seasonalPatterns;
        
        /**
         * Growth rate (%)
         */
        private Double growthRate;
        
        /**
         * Forecasted next period metrics
         */
        private ForecastMetrics forecast;
    }
    
    /**
     * Top Performers
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopPerformers {
        
        /**
         * Best performing days
         */
        private List<DayPerformance> bestDays;
        
        /**
         * Peak hours for orders
         */
        private List<HourPerformance> peakHours;
        
        /**
         * Most popular payment methods
         */
        private List<PaymentMethodMetric> popularPaymentMethods;
        
        /**
         * Most used shipping methods
         */
        private List<ShippingMethodMetric> popularShippingMethods;
    }
    
    /**
     * Supporting DTOs
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MetricsComparison {
        private Double changePercentage;
        private String trend; // UP, DOWN, STABLE
        private Long previousPeriodValue;
        private Long currentPeriodValue;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RevenueComparison {
        private Double changePercentage;
        private String trend;
        private Money previousPeriodRevenue;
        private Money currentPeriodRevenue;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailyRevenue {
        private LocalDateTime date;
        private Money revenue;
        private Long orderCount;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CustomerMetric {
        private Long customerId;
        private String customerName;
        private Long orderCount;
        private Money totalRevenue;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductMetric {
        private Long productId;
        private String productName;
        private String sku;
        private Long quantitySold;
        private Money revenue;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryMetric {
        private String category;
        private Long orderCount;
        private Money revenue;
        private Double averageOrderValue;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductReturnMetric {
        private Long productId;
        private String productName;
        private Long totalSold;
        private Long totalReturned;
        private Double returnRate;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SeasonalPattern {
        private String period; // MONTH, QUARTER, SEASON
        private String name;
        private Double averageOrderVolume;
        private Money averageRevenue;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ForecastMetrics {
        private Long predictedOrders;
        private Money predictedRevenue;
        private Double confidenceLevel;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DayPerformance {
        private LocalDateTime date;
        private Long orderCount;
        private Money revenue;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HourPerformance {
        private Integer hour;
        private Long orderCount;
        private Double percentage;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentMethodMetric {
        private String paymentMethod;
        private Long orderCount;
        private Double percentage;
        private Double successRate;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShippingMethodMetric {
        private String shippingMethod;
        private Long orderCount;
        private Double percentage;
        private Double averageDeliveryTime;
    }
    
    /**
     * Get period description
     */
    public String getPeriodDescription() {
        if (period == null) {
            return "Unknown period";
        }
        return period.getDescription();
    }
    
    /**
     * Get overall performance summary
     */
    public String getPerformanceSummary() {
        if (orderMetrics == null || revenueAnalytics == null) {
            return "Insufficient data";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append(orderMetrics.getTotalOrders()).append(" orders, ");
        summary.append(revenueAnalytics.getTotalRevenue().format()).append(" revenue");
        
        if (orderMetrics.getComparison() != null) {
            Double changePercentage = orderMetrics.getComparison().getChangePercentage();
            if (changePercentage != null) {
                summary.append(" (").append(changePercentage > 0 ? "+" : "")
                       .append(String.format("%.1f", changePercentage)).append("%)");
            }
        }
        
        return summary.toString();
    }
    
    /**
     * Check if performance is improving
     */
    public boolean isPerformanceImproving() {
        if (orderMetrics == null || orderMetrics.getComparison() == null) {
            return false;
        }
        
        return orderMetrics.getComparison().getChangePercentage() != null &&
               orderMetrics.getComparison().getChangePercentage() > 0;
    }
}
