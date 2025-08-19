package org.de013.orderservice.repository.custom.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.OrderItem;
import org.de013.orderservice.entity.OrderPayment;
import org.de013.orderservice.entity.OrderShipping;
import org.de013.orderservice.entity.OrderTracking;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.entity.enums.OrderType;
import org.de013.orderservice.repository.custom.OrderSearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Order Search Repository Implementation
 * 
 * Custom repository implementation for complex order search operations.
 * Uses JPA Criteria API for dynamic query building.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Repository
public class OrderSearchRepositoryImpl implements OrderSearchRepository {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    public Page<Order> searchOrders(OrderSearchCriteria searchCriteria, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Order> query = cb.createQuery(Order.class);
        Root<Order> root = query.from(Order.class);
        
        List<Predicate> predicates = buildPredicates(cb, root, searchCriteria);
        
        if (!predicates.isEmpty()) {
            query.where(cb.and(predicates.toArray(new Predicate[0])));
        }
        
        // Apply sorting
        applySorting(cb, query, root, searchCriteria, pageable);
        
        TypedQuery<Order> typedQuery = entityManager.createQuery(query);
        
        // Apply pagination
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        
        List<Order> results = typedQuery.getResultList();
        
        // Count query for pagination
        long total = countOrders(searchCriteria);
        
        return new PageImpl<>(results, pageable, total);
    }
    
    @Override
    public Page<Order> searchOrdersByText(String query, Pageable pageable) {
        if (!StringUtils.hasText(query)) {
            return Page.empty(pageable);
        }
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Order> criteriaQuery = cb.createQuery(Order.class);
        Root<Order> root = criteriaQuery.from(Order.class);
        
        String searchPattern = "%" + query.toLowerCase() + "%";
        
        List<Predicate> predicates = new ArrayList<>();
        
        // Search in order number
        predicates.add(cb.like(cb.lower(root.get("orderNumber")), searchPattern));
        
        // Search in customer notes
        predicates.add(cb.like(cb.lower(root.get("customerNotes")), searchPattern));
        
        // Search in internal notes
        predicates.add(cb.like(cb.lower(root.get("internalNotes")), searchPattern));
        
        // Search in shipping address
        predicates.add(cb.like(cb.lower(root.get("shippingAddress").get("firstName")), searchPattern));
        predicates.add(cb.like(cb.lower(root.get("shippingAddress").get("lastName")), searchPattern));
        predicates.add(cb.like(cb.lower(root.get("shippingAddress").get("email")), searchPattern));
        predicates.add(cb.like(cb.lower(root.get("shippingAddress").get("phone")), searchPattern));
        
        // Search in billing address
        predicates.add(cb.like(cb.lower(root.get("billingAddress").get("firstName")), searchPattern));
        predicates.add(cb.like(cb.lower(root.get("billingAddress").get("lastName")), searchPattern));
        predicates.add(cb.like(cb.lower(root.get("billingAddress").get("email")), searchPattern));
        
        criteriaQuery.where(cb.or(predicates.toArray(new Predicate[0])));
        criteriaQuery.orderBy(cb.desc(root.get("createdAt")));
        
        TypedQuery<Order> typedQuery = entityManager.createQuery(criteriaQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        
        List<Order> results = typedQuery.getResultList();
        
        // Count query
        long total = countOrdersByText(query);
        
        return new PageImpl<>(results, pageable, total);
    }
    
    @Override
    public Page<Order> findSimilarOrders(Long orderId, Pageable pageable) {
        // First, get the reference order
        Order referenceOrder = entityManager.find(Order.class, orderId);
        if (referenceOrder == null) {
            return Page.empty(pageable);
        }
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Order> query = cb.createQuery(Order.class);
        Root<Order> root = query.from(Order.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        // Exclude the reference order itself
        predicates.add(cb.notEqual(root.get("id"), orderId));
        
        // Same user
        predicates.add(cb.equal(root.get("userId"), referenceOrder.getUserId()));
        
        // Similar order type or amount range
        Predicate sameType = cb.equal(root.get("orderType"), referenceOrder.getOrderType());
        
        BigDecimal refAmount = referenceOrder.getTotalAmount().getAmount();
        BigDecimal minAmount = refAmount.multiply(new BigDecimal("0.8"));
        BigDecimal maxAmount = refAmount.multiply(new BigDecimal("1.2"));
        
        Predicate similarAmount = cb.and(
            cb.greaterThanOrEqualTo(root.get("totalAmount").get("amount"), minAmount),
            cb.lessThanOrEqualTo(root.get("totalAmount").get("amount"), maxAmount)
        );
        
        predicates.add(cb.or(sameType, similarAmount));
        
        query.where(cb.and(predicates.toArray(new Predicate[0])));
        query.orderBy(cb.desc(root.get("createdAt")));
        
        TypedQuery<Order> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        
        List<Order> results = typedQuery.getResultList();
        
        // Count query
        long total = countSimilarOrders(orderId);
        
        return new PageImpl<>(results, pageable, total);
    }
    
    @Override
    public Page<Order> searchOrdersWithFilters(
            Long userId, List<OrderStatus> statuses, List<OrderType> orderTypes,
            BigDecimal minAmount, BigDecimal maxAmount, String currency,
            LocalDateTime startDate, LocalDateTime endDate,
            String shippingCountry, String shippingCity,
            String customerEmail, String customerPhone,
            List<Long> productIds, Boolean isGift, Boolean requiresSpecialHandling,
            List<Integer> priorityLevels, Pageable pageable) {
        
        OrderSearchCriteria criteria = new OrderSearchCriteria();
        criteria.setUserId(userId);
        criteria.setStatuses(statuses);
        criteria.setOrderTypes(orderTypes);
        criteria.setMinAmount(minAmount);
        criteria.setMaxAmount(maxAmount);
        criteria.setCurrency(currency);
        criteria.setStartDate(startDate);
        criteria.setEndDate(endDate);
        criteria.setShippingCountry(shippingCountry);
        criteria.setShippingCity(shippingCity);
        criteria.setCustomerEmail(customerEmail);
        criteria.setCustomerPhone(customerPhone);
        criteria.setProductIds(productIds);
        criteria.setIsGift(isGift);
        criteria.setRequiresSpecialHandling(requiresSpecialHandling);
        criteria.setPriorityLevels(priorityLevels);
        
        return searchOrders(criteria, pageable);
    }
    
    @Override
    public Page<Order> searchOrdersByCustomer(String customerQuery, Pageable pageable) {
        if (!StringUtils.hasText(customerQuery)) {
            return Page.empty(pageable);
        }
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Order> query = cb.createQuery(Order.class);
        Root<Order> root = query.from(Order.class);
        
        String searchPattern = "%" + customerQuery.toLowerCase() + "%";
        
        List<Predicate> predicates = new ArrayList<>();
        
        // Search in shipping address
        predicates.add(cb.like(cb.lower(root.get("shippingAddress").get("firstName")), searchPattern));
        predicates.add(cb.like(cb.lower(root.get("shippingAddress").get("lastName")), searchPattern));
        predicates.add(cb.like(cb.lower(root.get("shippingAddress").get("email")), searchPattern));
        predicates.add(cb.like(cb.lower(root.get("shippingAddress").get("phone")), searchPattern));
        
        // Search in billing address
        predicates.add(cb.like(cb.lower(root.get("billingAddress").get("firstName")), searchPattern));
        predicates.add(cb.like(cb.lower(root.get("billingAddress").get("lastName")), searchPattern));
        predicates.add(cb.like(cb.lower(root.get("billingAddress").get("email")), searchPattern));
        predicates.add(cb.like(cb.lower(root.get("billingAddress").get("phone")), searchPattern));
        
        query.where(cb.or(predicates.toArray(new Predicate[0])));
        query.orderBy(cb.desc(root.get("createdAt")));
        
        TypedQuery<Order> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        
        List<Order> results = typedQuery.getResultList();
        
        // Count query
        long total = countOrdersByCustomer(customerQuery);
        
        return new PageImpl<>(results, pageable, total);
    }
    
    @Override
    public Page<Order> searchOrdersByProduct(String productQuery, Pageable pageable) {
        if (!StringUtils.hasText(productQuery)) {
            return Page.empty(pageable);
        }
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Order> query = cb.createQuery(Order.class);
        Root<Order> root = query.from(Order.class);
        Join<Order, OrderItem> itemJoin = root.join("orderItems", JoinType.INNER);
        
        String searchPattern = "%" + productQuery.toLowerCase() + "%";
        
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.like(cb.lower(itemJoin.get("productName")), searchPattern));
        predicates.add(cb.like(cb.lower(itemJoin.get("sku")), searchPattern));
        predicates.add(cb.like(cb.lower(itemJoin.get("productCategory")), searchPattern));
        predicates.add(cb.like(cb.lower(itemJoin.get("productBrand")), searchPattern));
        
        query.where(cb.or(predicates.toArray(new Predicate[0])));
        query.distinct(true);
        query.orderBy(cb.desc(root.get("createdAt")));
        
        TypedQuery<Order> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        
        List<Order> results = typedQuery.getResultList();
        
        // Count query
        long total = countOrdersByProduct(productQuery);
        
        return new PageImpl<>(results, pageable, total);
    }
    
    @Override
    public Page<Order> searchOrdersByTracking(String trackingQuery, Pageable pageable) {
        if (!StringUtils.hasText(trackingQuery)) {
            return Page.empty(pageable);
        }
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Order> query = cb.createQuery(Order.class);
        Root<Order> root = query.from(Order.class);
        
        String searchPattern = "%" + trackingQuery.toLowerCase() + "%";
        
        List<Predicate> predicates = new ArrayList<>();
        
        // Search in shipping tracking
        Join<Order, OrderShipping> shippingJoin = root.join("orderShipping", JoinType.LEFT);
        predicates.add(cb.like(cb.lower(shippingJoin.get("trackingNumber")), searchPattern));
        predicates.add(cb.like(cb.lower(shippingJoin.get("carrier")), searchPattern));
        
        // Search in tracking records
        Join<Order, OrderTracking> trackingJoin = root.join("orderTracking", JoinType.LEFT);
        predicates.add(cb.like(cb.lower(trackingJoin.get("trackingNumber")), searchPattern));
        predicates.add(cb.like(cb.lower(trackingJoin.get("carrier")), searchPattern));
        
        query.where(cb.or(predicates.toArray(new Predicate[0])));
        query.distinct(true);
        query.orderBy(cb.desc(root.get("createdAt")));
        
        TypedQuery<Order> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        
        List<Order> results = typedQuery.getResultList();
        
        // Count query
        long total = countOrdersByTracking(trackingQuery);
        
        return new PageImpl<>(results, pageable, total);
    }
    
    @Override
    public Page<Order> searchOrdersByPayment(String paymentQuery, Pageable pageable) {
        if (!StringUtils.hasText(paymentQuery)) {
            return Page.empty(pageable);
        }
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Order> query = cb.createQuery(Order.class);
        Root<Order> root = query.from(Order.class);
        Join<Order, OrderPayment> paymentJoin = root.join("orderPayments", JoinType.INNER);
        
        String searchPattern = "%" + paymentQuery.toLowerCase() + "%";
        
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.like(cb.lower(paymentJoin.get("paymentId")), searchPattern));
        predicates.add(cb.like(cb.lower(paymentJoin.get("transactionId")), searchPattern));
        predicates.add(cb.like(cb.lower(paymentJoin.get("paymentMethod")), searchPattern));
        predicates.add(cb.like(cb.lower(paymentJoin.get("paymentGateway")), searchPattern));
        
        query.where(cb.or(predicates.toArray(new Predicate[0])));
        query.distinct(true);
        query.orderBy(cb.desc(root.get("createdAt")));
        
        TypedQuery<Order> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        
        List<Order> results = typedQuery.getResultList();
        
        // Count query
        long total = countOrdersByPayment(paymentQuery);
        
        return new PageImpl<>(results, pageable, total);
    }
    
    @Override
    public Page<Order> findOrdersRequiringAttention(Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Order> query = cb.createQuery(Order.class);
        Root<Order> root = query.from(Order.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        // Overdue orders
        LocalDateTime now = LocalDateTime.now();
        Predicate overdue = cb.and(
            cb.lessThan(root.get("expectedDeliveryDate"), now),
            cb.isNull(root.get("actualDeliveryDate"))
        );
        
        // High priority orders
        Predicate highPriority = cb.lessThanOrEqualTo(root.get("priorityLevel"), 2);
        
        // Orders with failed payments
        Predicate failedPayments = cb.exists(
            cb.createQuery().subquery(OrderPayment.class)
                .where(cb.and(
                    cb.equal(cb.createQuery().from(OrderPayment.class).get("order"), root),
                    cb.in(cb.createQuery().from(OrderPayment.class).get("status"))
                        .value("FAILED").value("DECLINED").value("CANCELLED")
                ))
        );
        
        predicates.add(cb.or(overdue, highPriority, failedPayments));
        
        query.where(cb.and(predicates.toArray(new Predicate[0])));
        query.orderBy(cb.asc(root.get("priorityLevel")), cb.desc(root.get("createdAt")));
        
        TypedQuery<Order> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        
        List<Order> results = typedQuery.getResultList();
        
        // Count query
        long total = countOrdersRequiringAttention();
        
        return new PageImpl<>(results, pageable, total);
    }
    
    @Override
    public Page<Order> findOrdersWithAnomalies(Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Order> query = cb.createQuery(Order.class);
        Root<Order> root = query.from(Order.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        // Orders with unusually high amounts
        Predicate highAmount = cb.greaterThan(root.get("totalAmount").get("amount"), new BigDecimal("10000"));
        
        // Orders with many items
        Predicate manyItems = cb.greaterThan(cb.size(root.get("orderItems")), 20);
        
        // Orders with special handling requirements
        Predicate specialHandling = cb.isTrue(root.get("requiresSpecialHandling"));
        
        predicates.add(cb.or(highAmount, manyItems, specialHandling));
        
        query.where(cb.and(predicates.toArray(new Predicate[0])));
        query.orderBy(cb.desc(root.get("totalAmount").get("amount")));
        
        TypedQuery<Order> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        
        List<Order> results = typedQuery.getResultList();
        
        // Count query
        long total = countOrdersWithAnomalies();
        
        return new PageImpl<>(results, pageable, total);
    }
    
    @Override
    public List<OrderGroupData> searchOrdersGroupedByDate(LocalDateTime startDate, LocalDateTime endDate, String groupBy) {
        // This would be implemented based on specific database and requirements
        // For now, return empty list
        return new ArrayList<>();
    }
    
    // Helper methods for building predicates and counting
    
    private List<Predicate> buildPredicates(CriteriaBuilder cb, Root<Order> root, OrderSearchCriteria criteria) {
        List<Predicate> predicates = new ArrayList<>();
        
        if (criteria.getUserId() != null) {
            predicates.add(cb.equal(root.get("userId"), criteria.getUserId()));
        }
        
        if (criteria.getStatuses() != null && !criteria.getStatuses().isEmpty()) {
            predicates.add(root.get("status").in(criteria.getStatuses()));
        }
        
        if (criteria.getOrderTypes() != null && !criteria.getOrderTypes().isEmpty()) {
            predicates.add(root.get("orderType").in(criteria.getOrderTypes()));
        }
        
        if (criteria.getMinAmount() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("totalAmount").get("amount"), criteria.getMinAmount()));
        }
        
        if (criteria.getMaxAmount() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("totalAmount").get("amount"), criteria.getMaxAmount()));
        }
        
        if (StringUtils.hasText(criteria.getCurrency())) {
            predicates.add(cb.equal(root.get("totalAmount").get("currency"), criteria.getCurrency()));
        }
        
        if (criteria.getStartDate() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), criteria.getStartDate()));
        }
        
        if (criteria.getEndDate() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), criteria.getEndDate()));
        }
        
        if (StringUtils.hasText(criteria.getShippingCountry())) {
            predicates.add(cb.equal(root.get("shippingAddress").get("country"), criteria.getShippingCountry()));
        }
        
        if (StringUtils.hasText(criteria.getShippingCity())) {
            predicates.add(cb.like(cb.lower(root.get("shippingAddress").get("city")), 
                                 "%" + criteria.getShippingCity().toLowerCase() + "%"));
        }
        
        if (StringUtils.hasText(criteria.getCustomerEmail())) {
            Predicate shippingEmail = cb.like(cb.lower(root.get("shippingAddress").get("email")), 
                                            "%" + criteria.getCustomerEmail().toLowerCase() + "%");
            Predicate billingEmail = cb.like(cb.lower(root.get("billingAddress").get("email")), 
                                           "%" + criteria.getCustomerEmail().toLowerCase() + "%");
            predicates.add(cb.or(shippingEmail, billingEmail));
        }
        
        if (criteria.getIsGift() != null) {
            predicates.add(cb.equal(root.get("isGift"), criteria.getIsGift()));
        }
        
        if (criteria.getRequiresSpecialHandling() != null) {
            predicates.add(cb.equal(root.get("requiresSpecialHandling"), criteria.getRequiresSpecialHandling()));
        }
        
        if (criteria.getPriorityLevels() != null && !criteria.getPriorityLevels().isEmpty()) {
            predicates.add(root.get("priorityLevel").in(criteria.getPriorityLevels()));
        }
        
        return predicates;
    }
    
    private void applySorting(CriteriaBuilder cb, CriteriaQuery<Order> query, Root<Order> root,
                             OrderSearchCriteria criteria, Pageable pageable) {
        List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();
        
        if (StringUtils.hasText(criteria.getSortBy())) {
            String sortBy = criteria.getSortBy();
            boolean ascending = !"DESC".equalsIgnoreCase(criteria.getSortDirection());
            
            switch (sortBy.toLowerCase()) {
                case "createdat":
                    orders.add(ascending ? cb.asc(root.get("createdAt")) : cb.desc(root.get("createdAt")));
                    break;
                case "totalamount":
                    orders.add(ascending ? cb.asc(root.get("totalAmount").get("amount")) : 
                                         cb.desc(root.get("totalAmount").get("amount")));
                    break;
                case "status":
                    orders.add(ascending ? cb.asc(root.get("status")) : cb.desc(root.get("status")));
                    break;
                default:
                    orders.add(cb.desc(root.get("createdAt")));
            }
        } else {
            // Apply default sorting from Pageable
            pageable.getSort().forEach(order -> {
                if (order.isAscending()) {
                    orders.add(cb.asc(root.get(order.getProperty())));
                } else {
                    orders.add(cb.desc(root.get(order.getProperty())));
                }
            });
            
            // Default sort if no sorting specified
            if (orders.isEmpty()) {
                orders.add(cb.desc(root.get("createdAt")));
            }
        }
        
        query.orderBy(orders);
    }
    
    // Count methods (simplified implementations)
    
    private long countOrders(OrderSearchCriteria criteria) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Order> root = query.from(Order.class);
        
        List<Predicate> predicates = buildPredicates(cb, root, criteria);
        
        if (!predicates.isEmpty()) {
            query.where(cb.and(predicates.toArray(new Predicate[0])));
        }
        
        query.select(cb.count(root));
        
        return entityManager.createQuery(query).getSingleResult();
    }
    
    private long countOrdersByText(String searchQuery) {
        // Simplified count implementation
        return 0L;
    }
    
    private long countSimilarOrders(Long orderId) {
        // Simplified count implementation
        return 0L;
    }
    
    private long countOrdersByCustomer(String customerQuery) {
        // Simplified count implementation
        return 0L;
    }
    
    private long countOrdersByProduct(String productQuery) {
        // Simplified count implementation
        return 0L;
    }
    
    private long countOrdersByTracking(String trackingQuery) {
        // Simplified count implementation
        return 0L;
    }
    
    private long countOrdersByPayment(String paymentQuery) {
        // Simplified count implementation
        return 0L;
    }
    
    private long countOrdersRequiringAttention() {
        // Simplified count implementation
        return 0L;
    }
    
    private long countOrdersWithAnomalies() {
        // Simplified count implementation
        return 0L;
    }
}
