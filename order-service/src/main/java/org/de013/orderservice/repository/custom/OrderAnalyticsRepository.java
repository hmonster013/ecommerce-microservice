package org.de013.orderservice.repository.custom;

import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.entity.enums.OrderType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Order Analytics Repository Interface
 * 
 * Custom repository interface for order analytics and reporting queries.
 * Provides comprehensive business intelligence and metrics.
 * 
 * @author Development Team
 * @version 1.0.0
 */
public interface OrderAnalyticsRepository {
    
    /**
     * Get comprehensive order metrics for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return order metrics
     */
    OrderMetrics getOrderMetrics(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get revenue analytics for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return revenue analytics
     */
    RevenueAnalytics getRevenueAnalytics(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get customer analytics for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return customer analytics
     */
    CustomerAnalytics getCustomerAnalytics(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get product analytics for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return product analytics
     */
    ProductAnalytics getProductAnalytics(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get geographic analytics for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return geographic analytics
     */
    GeographicAnalytics getGeographicAnalytics(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get performance metrics for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return performance metrics
     */
    PerformanceMetrics getPerformanceMetrics(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get daily order trends
     * 
     * @param startDate start date
     * @param endDate end date
     * @return daily trends
     */
    List<DailyTrend> getDailyOrderTrends(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get hourly order patterns
     * 
     * @param startDate start date
     * @param endDate end date
     * @return hourly patterns
     */
    List<HourlyPattern> getHourlyOrderPatterns(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get top customers by order count
     * 
     * @param startDate start date
     * @param endDate end date
     * @param limit number of top customers
     * @return top customers
     */
    List<TopCustomer> getTopCustomersByOrderCount(LocalDateTime startDate, LocalDateTime endDate, int limit);
    
    /**
     * Get top customers by revenue
     * 
     * @param startDate start date
     * @param endDate end date
     * @param limit number of top customers
     * @return top customers by revenue
     */
    List<TopCustomer> getTopCustomersByRevenue(LocalDateTime startDate, LocalDateTime endDate, int limit);
    
    /**
     * Get top products by quantity sold
     * 
     * @param startDate start date
     * @param endDate end date
     * @param limit number of top products
     * @return top products
     */
    List<TopProduct> getTopProductsByQuantity(LocalDateTime startDate, LocalDateTime endDate, int limit);
    
    /**
     * Get top products by revenue
     * 
     * @param startDate start date
     * @param endDate end date
     * @param limit number of top products
     * @return top products by revenue
     */
    List<TopProduct> getTopProductsByRevenue(LocalDateTime startDate, LocalDateTime endDate, int limit);
    
    /**
     * Get conversion funnel data
     * 
     * @param startDate start date
     * @param endDate end date
     * @return conversion funnel
     */
    ConversionFunnel getConversionFunnel(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get cohort analysis data
     * 
     * @param startDate start date
     * @param endDate end date
     * @param cohortType cohort type (MONTHLY, WEEKLY)
     * @return cohort analysis
     */
    List<CohortData> getCohortAnalysis(LocalDateTime startDate, LocalDateTime endDate, String cohortType);
    
    /**
     * Get seasonal trends
     * 
     * @param year the year to analyze
     * @return seasonal trends
     */
    List<SeasonalTrend> getSeasonalTrends(int year);
    
    /**
     * Get order status distribution
     * 
     * @param startDate start date
     * @param endDate end date
     * @return status distribution
     */
    Map<OrderStatus, Long> getOrderStatusDistribution(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get order type distribution
     * 
     * @param startDate start date
     * @param endDate end date
     * @return type distribution
     */
    Map<OrderType, Long> getOrderTypeDistribution(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get payment method distribution
     * 
     * @param startDate start date
     * @param endDate end date
     * @return payment method distribution
     */
    Map<String, PaymentMethodStats> getPaymentMethodDistribution(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get shipping method distribution
     * 
     * @param startDate start date
     * @param endDate end date
     * @return shipping method distribution
     */
    Map<String, ShippingMethodStats> getShippingMethodDistribution(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get order source distribution
     * 
     * @param startDate start date
     * @param endDate end date
     * @return order source distribution
     */
    Map<String, Long> getOrderSourceDistribution(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get average order processing times
     * 
     * @param startDate start date
     * @param endDate end date
     * @return processing times by stage
     */
    Map<String, Double> getAverageProcessingTimes(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get customer lifetime value distribution
     * 
     * @param startDate start date
     * @param endDate end date
     * @return CLV distribution
     */
    List<CLVSegment> getCustomerLifetimeValueDistribution(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get order value distribution
     * 
     * @param startDate start date
     * @param endDate end date
     * @return order value segments
     */
    List<OrderValueSegment> getOrderValueDistribution(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get return and refund analytics
     * 
     * @param startDate start date
     * @param endDate end date
     * @return return analytics
     */
    ReturnAnalytics getReturnAnalytics(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get fraud detection metrics
     * 
     * @param startDate start date
     * @param endDate end date
     * @return fraud metrics
     */
    FraudMetrics getFraudMetrics(LocalDateTime startDate, LocalDateTime endDate);
    
    // Data Transfer Objects for Analytics
    
    class OrderMetrics {
        private Long totalOrders;
        private Long completedOrders;
        private Long cancelledOrders;
        private Double completionRate;
        private Double cancellationRate;
        private Double averageOrderValue;
        private Long totalCustomers;
        private Long newCustomers;
        private Long returningCustomers;
        private Double customerRetentionRate;
        
        // Constructors, getters, and setters
        public OrderMetrics() {}
        
        public OrderMetrics(Long totalOrders, Long completedOrders, Long cancelledOrders,
                           Double completionRate, Double cancellationRate, Double averageOrderValue,
                           Long totalCustomers, Long newCustomers, Long returningCustomers,
                           Double customerRetentionRate) {
            this.totalOrders = totalOrders;
            this.completedOrders = completedOrders;
            this.cancelledOrders = cancelledOrders;
            this.completionRate = completionRate;
            this.cancellationRate = cancellationRate;
            this.averageOrderValue = averageOrderValue;
            this.totalCustomers = totalCustomers;
            this.newCustomers = newCustomers;
            this.returningCustomers = returningCustomers;
            this.customerRetentionRate = customerRetentionRate;
        }
        
        // Getters and setters
        public Long getTotalOrders() { return totalOrders; }
        public void setTotalOrders(Long totalOrders) { this.totalOrders = totalOrders; }
        
        public Long getCompletedOrders() { return completedOrders; }
        public void setCompletedOrders(Long completedOrders) { this.completedOrders = completedOrders; }
        
        public Long getCancelledOrders() { return cancelledOrders; }
        public void setCancelledOrders(Long cancelledOrders) { this.cancelledOrders = cancelledOrders; }
        
        public Double getCompletionRate() { return completionRate; }
        public void setCompletionRate(Double completionRate) { this.completionRate = completionRate; }
        
        public Double getCancellationRate() { return cancellationRate; }
        public void setCancellationRate(Double cancellationRate) { this.cancellationRate = cancellationRate; }
        
        public Double getAverageOrderValue() { return averageOrderValue; }
        public void setAverageOrderValue(Double averageOrderValue) { this.averageOrderValue = averageOrderValue; }
        
        public Long getTotalCustomers() { return totalCustomers; }
        public void setTotalCustomers(Long totalCustomers) { this.totalCustomers = totalCustomers; }
        
        public Long getNewCustomers() { return newCustomers; }
        public void setNewCustomers(Long newCustomers) { this.newCustomers = newCustomers; }
        
        public Long getReturningCustomers() { return returningCustomers; }
        public void setReturningCustomers(Long returningCustomers) { this.returningCustomers = returningCustomers; }
        
        public Double getCustomerRetentionRate() { return customerRetentionRate; }
        public void setCustomerRetentionRate(Double customerRetentionRate) { this.customerRetentionRate = customerRetentionRate; }
    }
    
    class RevenueAnalytics {
        private BigDecimal totalRevenue;
        private BigDecimal netRevenue;
        private BigDecimal totalRefunds;
        private BigDecimal totalTax;
        private BigDecimal totalShipping;
        private BigDecimal totalDiscounts;
        private String currency;
        private Double growthRate;
        
        // Constructors, getters, and setters
        public RevenueAnalytics() {}
        
        public BigDecimal getTotalRevenue() { return totalRevenue; }
        public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
        
        public BigDecimal getNetRevenue() { return netRevenue; }
        public void setNetRevenue(BigDecimal netRevenue) { this.netRevenue = netRevenue; }
        
        public BigDecimal getTotalRefunds() { return totalRefunds; }
        public void setTotalRefunds(BigDecimal totalRefunds) { this.totalRefunds = totalRefunds; }
        
        public BigDecimal getTotalTax() { return totalTax; }
        public void setTotalTax(BigDecimal totalTax) { this.totalTax = totalTax; }
        
        public BigDecimal getTotalShipping() { return totalShipping; }
        public void setTotalShipping(BigDecimal totalShipping) { this.totalShipping = totalShipping; }
        
        public BigDecimal getTotalDiscounts() { return totalDiscounts; }
        public void setTotalDiscounts(BigDecimal totalDiscounts) { this.totalDiscounts = totalDiscounts; }
        
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        
        public Double getGrowthRate() { return growthRate; }
        public void setGrowthRate(Double growthRate) { this.growthRate = growthRate; }
    }
    
    class CustomerAnalytics {
        private Long totalCustomers;
        private Long newCustomers;
        private Long returningCustomers;
        private Double retentionRate;
        private Double averageOrdersPerCustomer;
        private BigDecimal customerLifetimeValue;
        
        // Constructors, getters, and setters
        public CustomerAnalytics() {}
        
        public Long getTotalCustomers() { return totalCustomers; }
        public void setTotalCustomers(Long totalCustomers) { this.totalCustomers = totalCustomers; }
        
        public Long getNewCustomers() { return newCustomers; }
        public void setNewCustomers(Long newCustomers) { this.newCustomers = newCustomers; }
        
        public Long getReturningCustomers() { return returningCustomers; }
        public void setReturningCustomers(Long returningCustomers) { this.returningCustomers = returningCustomers; }
        
        public Double getRetentionRate() { return retentionRate; }
        public void setRetentionRate(Double retentionRate) { this.retentionRate = retentionRate; }
        
        public Double getAverageOrdersPerCustomer() { return averageOrdersPerCustomer; }
        public void setAverageOrdersPerCustomer(Double averageOrdersPerCustomer) { this.averageOrdersPerCustomer = averageOrdersPerCustomer; }
        
        public BigDecimal getCustomerLifetimeValue() { return customerLifetimeValue; }
        public void setCustomerLifetimeValue(BigDecimal customerLifetimeValue) { this.customerLifetimeValue = customerLifetimeValue; }
    }
    
    class ProductAnalytics {
        private Long totalProducts;
        private Long totalItemsSold;
        private Double averageItemsPerOrder;
        private String topSellingProduct;
        private String topRevenueProduct;
        private String topCategory;
        
        // Constructors, getters, and setters
        public ProductAnalytics() {}
        
        public Long getTotalProducts() { return totalProducts; }
        public void setTotalProducts(Long totalProducts) { this.totalProducts = totalProducts; }
        
        public Long getTotalItemsSold() { return totalItemsSold; }
        public void setTotalItemsSold(Long totalItemsSold) { this.totalItemsSold = totalItemsSold; }
        
        public Double getAverageItemsPerOrder() { return averageItemsPerOrder; }
        public void setAverageItemsPerOrder(Double averageItemsPerOrder) { this.averageItemsPerOrder = averageItemsPerOrder; }
        
        public String getTopSellingProduct() { return topSellingProduct; }
        public void setTopSellingProduct(String topSellingProduct) { this.topSellingProduct = topSellingProduct; }
        
        public String getTopRevenueProduct() { return topRevenueProduct; }
        public void setTopRevenueProduct(String topRevenueProduct) { this.topRevenueProduct = topRevenueProduct; }
        
        public String getTopCategory() { return topCategory; }
        public void setTopCategory(String topCategory) { this.topCategory = topCategory; }
    }
    
    class GeographicAnalytics {
        private Map<String, Long> ordersByCountry;
        private Map<String, Long> ordersByState;
        private Map<String, Long> ordersByCity;
        private String topCountry;
        private String topState;
        private String topCity;
        
        // Constructors, getters, and setters
        public GeographicAnalytics() {}
        
        public Map<String, Long> getOrdersByCountry() { return ordersByCountry; }
        public void setOrdersByCountry(Map<String, Long> ordersByCountry) { this.ordersByCountry = ordersByCountry; }
        
        public Map<String, Long> getOrdersByState() { return ordersByState; }
        public void setOrdersByState(Map<String, Long> ordersByState) { this.ordersByState = ordersByState; }
        
        public Map<String, Long> getOrdersByCity() { return ordersByCity; }
        public void setOrdersByCity(Map<String, Long> ordersByCity) { this.ordersByCity = ordersByCity; }
        
        public String getTopCountry() { return topCountry; }
        public void setTopCountry(String topCountry) { this.topCountry = topCountry; }
        
        public String getTopState() { return topState; }
        public void setTopState(String topState) { this.topState = topState; }
        
        public String getTopCity() { return topCity; }
        public void setTopCity(String topCity) { this.topCity = topCity; }
    }
    
    class PerformanceMetrics {
        private Double averageProcessingTime;
        private Double averageDeliveryTime;
        private Double onTimeDeliveryRate;
        private Double paymentSuccessRate;
        private Double customerSatisfactionScore;
        
        // Constructors, getters, and setters
        public PerformanceMetrics() {}
        
        public Double getAverageProcessingTime() { return averageProcessingTime; }
        public void setAverageProcessingTime(Double averageProcessingTime) { this.averageProcessingTime = averageProcessingTime; }
        
        public Double getAverageDeliveryTime() { return averageDeliveryTime; }
        public void setAverageDeliveryTime(Double averageDeliveryTime) { this.averageDeliveryTime = averageDeliveryTime; }
        
        public Double getOnTimeDeliveryRate() { return onTimeDeliveryRate; }
        public void setOnTimeDeliveryRate(Double onTimeDeliveryRate) { this.onTimeDeliveryRate = onTimeDeliveryRate; }
        
        public Double getPaymentSuccessRate() { return paymentSuccessRate; }
        public void setPaymentSuccessRate(Double paymentSuccessRate) { this.paymentSuccessRate = paymentSuccessRate; }
        
        public Double getCustomerSatisfactionScore() { return customerSatisfactionScore; }
        public void setCustomerSatisfactionScore(Double customerSatisfactionScore) { this.customerSatisfactionScore = customerSatisfactionScore; }
    }
    
    // Additional DTOs would be defined here for other analytics classes
    // (DailyTrend, HourlyPattern, TopCustomer, TopProduct, etc.)
    
    class DailyTrend {
        private LocalDateTime date;
        private Long orderCount;
        private BigDecimal revenue;
        
        public DailyTrend() {}
        
        public DailyTrend(LocalDateTime date, Long orderCount, BigDecimal revenue) {
            this.date = date;
            this.orderCount = orderCount;
            this.revenue = revenue;
        }
        
        public LocalDateTime getDate() { return date; }
        public void setDate(LocalDateTime date) { this.date = date; }
        
        public Long getOrderCount() { return orderCount; }
        public void setOrderCount(Long orderCount) { this.orderCount = orderCount; }
        
        public BigDecimal getRevenue() { return revenue; }
        public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
    }
    
    class HourlyPattern {
        private Integer hour;
        private Long orderCount;
        private Double percentage;
        
        public HourlyPattern() {}
        
        public HourlyPattern(Integer hour, Long orderCount, Double percentage) {
            this.hour = hour;
            this.orderCount = orderCount;
            this.percentage = percentage;
        }
        
        public Integer getHour() { return hour; }
        public void setHour(Integer hour) { this.hour = hour; }
        
        public Long getOrderCount() { return orderCount; }
        public void setOrderCount(Long orderCount) { this.orderCount = orderCount; }
        
        public Double getPercentage() { return percentage; }
        public void setPercentage(Double percentage) { this.percentage = percentage; }
    }
    
    class TopCustomer {
        private Long customerId;
        private String customerName;
        private Long orderCount;
        private BigDecimal totalRevenue;
        
        public TopCustomer() {}
        
        public TopCustomer(Long customerId, String customerName, Long orderCount, BigDecimal totalRevenue) {
            this.customerId = customerId;
            this.customerName = customerName;
            this.orderCount = orderCount;
            this.totalRevenue = totalRevenue;
        }
        
        public Long getCustomerId() { return customerId; }
        public void setCustomerId(Long customerId) { this.customerId = customerId; }
        
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        
        public Long getOrderCount() { return orderCount; }
        public void setOrderCount(Long orderCount) { this.orderCount = orderCount; }
        
        public BigDecimal getTotalRevenue() { return totalRevenue; }
        public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
    }
    
    class TopProduct {
        private Long productId;
        private String productName;
        private String sku;
        private Long quantitySold;
        private BigDecimal revenue;
        
        public TopProduct() {}
        
        public TopProduct(Long productId, String productName, String sku, Long quantitySold, BigDecimal revenue) {
            this.productId = productId;
            this.productName = productName;
            this.sku = sku;
            this.quantitySold = quantitySold;
            this.revenue = revenue;
        }
        
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        
        public Long getQuantitySold() { return quantitySold; }
        public void setQuantitySold(Long quantitySold) { this.quantitySold = quantitySold; }
        
        public BigDecimal getRevenue() { return revenue; }
        public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
    }
    
    // Placeholder classes for other DTOs
    class ConversionFunnel {}
    class CohortData {}
    class SeasonalTrend {}
    class PaymentMethodStats {}
    class ShippingMethodStats {}
    class CLVSegment {}
    class OrderValueSegment {}
    class ReturnAnalytics {}
    class FraudMetrics {}
}
