package org.de013.paymentservice.service;

import org.de013.paymentservice.dto.paymentmethod.CreatePaymentMethodRequest;
import org.de013.paymentservice.dto.paymentmethod.PaymentMethodResponse;
import org.de013.paymentservice.dto.paymentmethod.UpdatePaymentMethodRequest;
import org.de013.paymentservice.entity.PaymentMethod;
import org.de013.paymentservice.entity.enums.PaymentMethodType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for payment method operations
 */
public interface PaymentMethodService {

    // ========== PAYMENT METHOD CRUD ==========

    /**
     * Create a new payment method
     */
    PaymentMethodResponse createPaymentMethod(CreatePaymentMethodRequest request);

    /**
     * Get payment method by ID
     */
    Optional<PaymentMethodResponse> getPaymentMethodById(Long paymentMethodId);

    /**
     * Get payment method by Stripe payment method ID
     */
    Optional<PaymentMethodResponse> getPaymentMethodByStripeId(String stripePaymentMethodId);

    /**
     * Update payment method
     */
    PaymentMethodResponse updatePaymentMethod(Long paymentMethodId, UpdatePaymentMethodRequest request);

    /**
     * Delete payment method (soft delete)
     */
    void deletePaymentMethod(Long paymentMethodId);

    /**
     * Activate payment method
     */
    PaymentMethodResponse activatePaymentMethod(Long paymentMethodId);

    /**
     * Deactivate payment method
     */
    PaymentMethodResponse deactivatePaymentMethod(Long paymentMethodId);

    // ========== USER PAYMENT METHODS ==========

    /**
     * Get all payment methods for user
     */
    List<PaymentMethodResponse> getPaymentMethodsByUserId(Long userId);

    /**
     * Get active payment methods for user
     */
    List<PaymentMethodResponse> getActivePaymentMethodsByUserId(Long userId);

    /**
     * Get payment methods by user ID with pagination
     */
    Page<PaymentMethodResponse> getPaymentMethodsByUserId(Long userId, Pageable pageable);

    /**
     * Get payment methods by user ID and type
     */
    List<PaymentMethodResponse> getPaymentMethodsByUserIdAndType(Long userId, PaymentMethodType type);

    // ========== DEFAULT PAYMENT METHOD MANAGEMENT ==========

    /**
     * Get default payment method for user
     */
    Optional<PaymentMethodResponse> getDefaultPaymentMethodByUserId(Long userId);

    /**
     * Set payment method as default for user
     */
    PaymentMethodResponse setAsDefaultPaymentMethod(Long paymentMethodId);

    /**
     * Clear default payment method for user
     */
    void clearDefaultPaymentMethod(Long userId);

    /**
     * Check if user has default payment method
     */
    boolean hasDefaultPaymentMethod(Long userId);

    // ========== STRIPE INTEGRATION ==========

    /**
     * Attach payment method to Stripe customer
     */
    PaymentMethodResponse attachPaymentMethodToCustomer(Long paymentMethodId, String stripeCustomerId);

    /**
     * Detach payment method from Stripe customer
     */
    PaymentMethodResponse detachPaymentMethodFromCustomer(Long paymentMethodId);

    /**
     * Sync payment method with Stripe
     */
    PaymentMethodResponse syncPaymentMethodWithStripe(Long paymentMethodId);

    /**
     * Get payment methods from Stripe for customer
     */
    List<PaymentMethodResponse> getStripePaymentMethodsForCustomer(String stripeCustomerId);

    // ========== CARD MANAGEMENT ==========

    /**
     * Get card payment methods for user
     */
    List<PaymentMethodResponse> getCardPaymentMethodsByUserId(Long userId);

    /**
     * Find expired card payment methods
     */
    List<PaymentMethodResponse> getExpiredCardPaymentMethods();

    /**
     * Find card payment methods expiring soon
     */
    List<PaymentMethodResponse> getCardPaymentMethodsExpiringSoon(int months);

    /**
     * Update card expiry date
     */
    PaymentMethodResponse updateCardExpiry(Long paymentMethodId, Integer expiryMonth, Integer expiryYear);

    // ========== PAYMENT METHOD SEARCH ==========

    /**
     * Search payment methods with criteria
     */
    Page<PaymentMethodResponse> searchPaymentMethods(
            Long userId,
            PaymentMethodType type,
            Boolean isActive,
            Boolean isDefault,
            String provider,
            String cardBrand,
            Pageable pageable
    );

    /**
     * Get recently used payment methods
     */
    List<PaymentMethodResponse> getRecentlyUsedPaymentMethods(Long userId, int limit);

    /**
     * Get unused payment methods
     */
    List<PaymentMethodResponse> getUnusedPaymentMethods(Long userId);

    // ========== PAYMENT METHOD STATISTICS ==========

    /**
     * Get payment method statistics for user
     */
    PaymentMethodStatistics getPaymentMethodStatisticsByUserId(Long userId);

    /**
     * Get payment method count by user and type
     */
    Long getPaymentMethodCountByUserAndType(Long userId, PaymentMethodType type);

    /**
     * Check if user has active payment methods
     */
    boolean hasActivePaymentMethods(Long userId);

    // ========== UTILITY METHODS ==========

    /**
     * Update last used timestamp
     */
    void updateLastUsedTimestamp(Long paymentMethodId);

    /**
     * Update payment method nickname
     */
    PaymentMethodResponse updatePaymentMethodNickname(Long paymentMethodId, String nickname);

    /**
     * Validate payment method ownership
     */
    void validatePaymentMethodOwnership(Long paymentMethodId, Long userId);

    /**
     * Get payment method entity by ID (for internal use)
     */
    Optional<PaymentMethod> getPaymentMethodEntityById(Long paymentMethodId);

    /**
     * Save payment method entity (for internal use)
     */
    PaymentMethod savePaymentMethodEntity(PaymentMethod paymentMethod);

    // ========== CLEANUP OPERATIONS ==========

    /**
     * Clean up inactive payment methods
     */
    void cleanupInactivePaymentMethods(LocalDateTime cutoffDate);

    /**
     * Clean up orphaned payment methods
     */
    void cleanupOrphanedPaymentMethods();

    // ========== PAYMENT METHOD STATISTICS DTO ==========

    /**
     * Payment method statistics data transfer object
     */
    record PaymentMethodStatistics(
            Long totalMethods,
            Long activeMethods,
            Long defaultMethods,
            Long cardMethods,
            Long walletMethods,
            Long expiredMethods,
            Long expiringSoonMethods,
            LocalDateTime oldestMethodDate,
            LocalDateTime newestMethodDate
    ) {}
}
