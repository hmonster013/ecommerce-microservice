package org.de013.orderservice.repository.custom;

import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.entity.enums.OrderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Search Repository Interface
 * 
 * Custom repository interface for complex order search operations.
 * Provides advanced filtering and search capabilities.
 * 
 * @author Development Team
 * @version 1.0.0
 */
public interface OrderSearchRepository {
    
    /**
     * Search orders with complex filters
     * 
     * @param searchCriteria the search criteria
     * @param pageable pagination information
     * @return page of matching orders
     */
    Page<Order> searchOrders(OrderSearchCriteria searchCriteria, Pageable pageable);
    
    /**
     * Search orders by text query
     * 
     * @param query text query to search in order number, customer info, notes
     * @param pageable pagination information
     * @return page of matching orders
     */
    Page<Order> searchOrdersByText(String query, Pageable pageable);
    
    /**
     * Find similar orders based on customer and product patterns
     * 
     * @param orderId the reference order ID
     * @param pageable pagination information
     * @return page of similar orders
     */
    Page<Order> findSimilarOrders(Long orderId, Pageable pageable);
    
    /**
     * Search orders with advanced filters
     * 
     * @param userId user ID filter (optional)
     * @param statuses order status filters (optional)
     * @param orderTypes order type filters (optional)
     * @param minAmount minimum total amount (optional)
     * @param maxAmount maximum total amount (optional)
     * @param currency currency filter (optional)
     * @param startDate start date filter (optional)
     * @param endDate end date filter (optional)
     * @param shippingCountry shipping country filter (optional)
     * @param shippingCity shipping city filter (optional)
     * @param customerEmail customer email filter (optional)
     * @param customerPhone customer phone filter (optional)
     * @param productIds product ID filters (optional)
     * @param isGift gift order filter (optional)
     * @param requiresSpecialHandling special handling filter (optional)
     * @param priorityLevels priority level filters (optional)
     * @param pageable pagination information
     * @return page of matching orders
     */
    Page<Order> searchOrdersWithFilters(
            Long userId,
            List<OrderStatus> statuses,
            List<OrderType> orderTypes,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String currency,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String shippingCountry,
            String shippingCity,
            String customerEmail,
            String customerPhone,
            List<Long> productIds,
            Boolean isGift,
            Boolean requiresSpecialHandling,
            List<Integer> priorityLevels,
            Pageable pageable
    );
    
    /**
     * Search orders by customer information
     * 
     * @param customerQuery customer name, email, or phone
     * @param pageable pagination information
     * @return page of matching orders
     */
    Page<Order> searchOrdersByCustomer(String customerQuery, Pageable pageable);
    
    /**
     * Search orders by product information
     * 
     * @param productQuery product name, SKU, or category
     * @param pageable pagination information
     * @return page of matching orders
     */
    Page<Order> searchOrdersByProduct(String productQuery, Pageable pageable);
    
    /**
     * Search orders by tracking information
     * 
     * @param trackingQuery tracking number or carrier
     * @param pageable pagination information
     * @return page of matching orders
     */
    Page<Order> searchOrdersByTracking(String trackingQuery, Pageable pageable);
    
    /**
     * Search orders by payment information
     * 
     * @param paymentQuery payment ID, transaction ID, or payment method
     * @param pageable pagination information
     * @return page of matching orders
     */
    Page<Order> searchOrdersByPayment(String paymentQuery, Pageable pageable);
    
    /**
     * Find orders requiring attention
     * 
     * @param pageable pagination information
     * @return page of orders requiring attention
     */
    Page<Order> findOrdersRequiringAttention(Pageable pageable);
    
    /**
     * Find orders with anomalies
     * 
     * @param pageable pagination information
     * @return page of orders with potential issues
     */
    Page<Order> findOrdersWithAnomalies(Pageable pageable);
    
    /**
     * Search orders by date range with grouping
     * 
     * @param startDate start date
     * @param endDate end date
     * @param groupBy grouping criteria (DAY, WEEK, MONTH)
     * @return grouped order data
     */
    List<OrderGroupData> searchOrdersGroupedByDate(LocalDateTime startDate, LocalDateTime endDate, String groupBy);
    
    /**
     * Order Search Criteria
     */
    class OrderSearchCriteria {
        private String query;
        private Long userId;
        private List<OrderStatus> statuses;
        private List<OrderType> orderTypes;
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private String currency;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private String shippingCountry;
        private String shippingState;
        private String shippingCity;
        private String customerEmail;
        private String customerPhone;
        private String customerName;
        private List<Long> productIds;
        private List<String> productCategories;
        private List<String> productBrands;
        private Boolean isGift;
        private Boolean requiresSpecialHandling;
        private List<Integer> priorityLevels;
        private String orderSource;
        private String paymentMethod;
        private String shippingMethod;
        private String carrier;
        private String trackingNumber;
        private Boolean hasTracking;
        private Boolean isDelivered;
        private Boolean isOverdue;
        private Boolean hasProblem;
        private String sortBy;
        private String sortDirection;
        
        // Constructors
        public OrderSearchCriteria() {}
        
        public OrderSearchCriteria(String query) {
            this.query = query;
        }
        
        // Getters and Setters
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        
        public List<OrderStatus> getStatuses() { return statuses; }
        public void setStatuses(List<OrderStatus> statuses) { this.statuses = statuses; }
        
        public List<OrderType> getOrderTypes() { return orderTypes; }
        public void setOrderTypes(List<OrderType> orderTypes) { this.orderTypes = orderTypes; }
        
        public BigDecimal getMinAmount() { return minAmount; }
        public void setMinAmount(BigDecimal minAmount) { this.minAmount = minAmount; }
        
        public BigDecimal getMaxAmount() { return maxAmount; }
        public void setMaxAmount(BigDecimal maxAmount) { this.maxAmount = maxAmount; }
        
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        
        public LocalDateTime getStartDate() { return startDate; }
        public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
        
        public LocalDateTime getEndDate() { return endDate; }
        public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
        
        public String getShippingCountry() { return shippingCountry; }
        public void setShippingCountry(String shippingCountry) { this.shippingCountry = shippingCountry; }
        
        public String getShippingState() { return shippingState; }
        public void setShippingState(String shippingState) { this.shippingState = shippingState; }
        
        public String getShippingCity() { return shippingCity; }
        public void setShippingCity(String shippingCity) { this.shippingCity = shippingCity; }
        
        public String getCustomerEmail() { return customerEmail; }
        public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
        
        public String getCustomerPhone() { return customerPhone; }
        public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
        
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        
        public List<Long> getProductIds() { return productIds; }
        public void setProductIds(List<Long> productIds) { this.productIds = productIds; }
        
        public List<String> getProductCategories() { return productCategories; }
        public void setProductCategories(List<String> productCategories) { this.productCategories = productCategories; }
        
        public List<String> getProductBrands() { return productBrands; }
        public void setProductBrands(List<String> productBrands) { this.productBrands = productBrands; }
        
        public Boolean getIsGift() { return isGift; }
        public void setIsGift(Boolean isGift) { this.isGift = isGift; }
        
        public Boolean getRequiresSpecialHandling() { return requiresSpecialHandling; }
        public void setRequiresSpecialHandling(Boolean requiresSpecialHandling) { this.requiresSpecialHandling = requiresSpecialHandling; }
        
        public List<Integer> getPriorityLevels() { return priorityLevels; }
        public void setPriorityLevels(List<Integer> priorityLevels) { this.priorityLevels = priorityLevels; }
        
        public String getOrderSource() { return orderSource; }
        public void setOrderSource(String orderSource) { this.orderSource = orderSource; }
        
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        
        public String getShippingMethod() { return shippingMethod; }
        public void setShippingMethod(String shippingMethod) { this.shippingMethod = shippingMethod; }
        
        public String getCarrier() { return carrier; }
        public void setCarrier(String carrier) { this.carrier = carrier; }
        
        public String getTrackingNumber() { return trackingNumber; }
        public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
        
        public Boolean getHasTracking() { return hasTracking; }
        public void setHasTracking(Boolean hasTracking) { this.hasTracking = hasTracking; }
        
        public Boolean getIsDelivered() { return isDelivered; }
        public void setIsDelivered(Boolean isDelivered) { this.isDelivered = isDelivered; }
        
        public Boolean getIsOverdue() { return isOverdue; }
        public void setIsOverdue(Boolean isOverdue) { this.isOverdue = isOverdue; }
        
        public Boolean getHasProblem() { return hasProblem; }
        public void setHasProblem(Boolean hasProblem) { this.hasProblem = hasProblem; }
        
        public String getSortBy() { return sortBy; }
        public void setSortBy(String sortBy) { this.sortBy = sortBy; }
        
        public String getSortDirection() { return sortDirection; }
        public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }
    }
    
    /**
     * Order Group Data
     */
    class OrderGroupData {
        private String groupKey;
        private Long orderCount;
        private BigDecimal totalAmount;
        private BigDecimal averageAmount;
        private String currency;
        private LocalDateTime periodStart;
        private LocalDateTime periodEnd;
        
        // Constructors
        public OrderGroupData() {}
        
        public OrderGroupData(String groupKey, Long orderCount, BigDecimal totalAmount, 
                             BigDecimal averageAmount, String currency, 
                             LocalDateTime periodStart, LocalDateTime periodEnd) {
            this.groupKey = groupKey;
            this.orderCount = orderCount;
            this.totalAmount = totalAmount;
            this.averageAmount = averageAmount;
            this.currency = currency;
            this.periodStart = periodStart;
            this.periodEnd = periodEnd;
        }
        
        // Getters and Setters
        public String getGroupKey() { return groupKey; }
        public void setGroupKey(String groupKey) { this.groupKey = groupKey; }
        
        public Long getOrderCount() { return orderCount; }
        public void setOrderCount(Long orderCount) { this.orderCount = orderCount; }
        
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        
        public BigDecimal getAverageAmount() { return averageAmount; }
        public void setAverageAmount(BigDecimal averageAmount) { this.averageAmount = averageAmount; }
        
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        
        public LocalDateTime getPeriodStart() { return periodStart; }
        public void setPeriodStart(LocalDateTime periodStart) { this.periodStart = periodStart; }
        
        public LocalDateTime getPeriodEnd() { return periodEnd; }
        public void setPeriodEnd(LocalDateTime periodEnd) { this.periodEnd = periodEnd; }
    }
}
