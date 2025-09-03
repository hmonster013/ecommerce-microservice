package org.de013.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.dto.paymentmethod.CreatePaymentMethodRequest;
import org.de013.paymentservice.dto.paymentmethod.PaymentMethodResponse;
import org.de013.paymentservice.dto.paymentmethod.UpdatePaymentMethodRequest;
import org.de013.paymentservice.dto.stripe.StripePaymentMethodRequest;
import org.de013.paymentservice.dto.stripe.StripePaymentMethodResponse;
import org.de013.paymentservice.entity.PaymentMethod;
import org.de013.paymentservice.entity.enums.PaymentMethodType;
import org.de013.paymentservice.exception.PaymentMethodNotFoundException;
import org.de013.paymentservice.exception.PaymentProcessingException;
import org.de013.paymentservice.gateway.PaymentGatewayFactory;
import org.de013.paymentservice.gateway.stripe.StripePaymentGateway;
import org.de013.paymentservice.mapper.PaymentMethodMapper;
import org.de013.paymentservice.repository.PaymentMethodRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of PaymentMethodService
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentMethodServiceImpl implements PaymentMethodService {

    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentGatewayFactory gatewayFactory;
    private final PaymentMethodMapper paymentMethodMapper;

    // ========== PAYMENT METHOD CRUD ==========

    @Override
    public PaymentMethodResponse createPaymentMethod(CreatePaymentMethodRequest request) {
        log.info("Creating payment method for user: {}, type: {}", request.getUserId(), request.getType());

        try {
            // Create payment method with Stripe
            StripePaymentGateway stripeGateway = gatewayFactory.getStripeGateway();
            StripePaymentMethodRequest stripeRequest = stripeGateway.mapToGatewayPaymentMethodRequest(request);
            StripePaymentMethodResponse stripeResponse = stripeGateway.createPaymentMethod(stripeRequest);

            // Create payment method entity
            PaymentMethod paymentMethod = createPaymentMethodEntity(request, stripeResponse);
            paymentMethod = paymentMethodRepository.save(paymentMethod);

            // Attach to customer if customer ID provided
            if (request.getStripeCustomerId() != null) {
                stripeGateway.attachPaymentMethod(stripeResponse.getPaymentMethodId(), request.getStripeCustomerId());
                paymentMethod.setStripeCustomerId(request.getStripeCustomerId());
                paymentMethod = paymentMethodRepository.save(paymentMethod);
            }

            // Set as default if requested
            if (request.getSetAsDefault() != null && request.getSetAsDefault()) {
                setAsDefaultPaymentMethod(paymentMethod.getId());
            }

            log.info("Payment method created successfully: {}", paymentMethod.getId());
            return paymentMethodMapper.toPaymentMethodResponse(paymentMethod);

        } catch (Exception e) {
            log.error("Failed to create payment method for user: {}", request.getUserId(), e);
            throw new PaymentProcessingException("Failed to create payment method: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentMethodResponse> getPaymentMethodById(Long paymentMethodId) {
        return paymentMethodRepository.findById(paymentMethodId)
                .map(paymentMethodMapper::toPaymentMethodResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentMethodResponse> getPaymentMethodByStripeId(String stripePaymentMethodId) {
        return paymentMethodRepository.findByStripePaymentMethodId(stripePaymentMethodId)
                .map(paymentMethodMapper::toPaymentMethodResponse);
    }

    @Override
    public PaymentMethodResponse updatePaymentMethod(Long paymentMethodId, UpdatePaymentMethodRequest request) {
        PaymentMethod paymentMethod = getPaymentMethodEntityById(paymentMethodId)
                .orElseThrow(() -> new PaymentMethodNotFoundException("Payment method not found: " + paymentMethodId));

        // Update fields
        if (request.getNickname() != null) {
            paymentMethod.setNickname(request.getNickname());
        }

        if (request.getSetAsDefault() != null && request.getSetAsDefault()) {
            clearDefaultPaymentMethod(paymentMethod.getUserId());
            paymentMethod.setIsDefault(true);
        }

        paymentMethod = paymentMethodRepository.save(paymentMethod);

        log.info("Payment method updated: {}", paymentMethodId);
        return paymentMethodMapper.toPaymentMethodResponse(paymentMethod);
    }

    @Override
    public void deletePaymentMethod(Long paymentMethodId) {
        PaymentMethod paymentMethod = getPaymentMethodEntityById(paymentMethodId)
                .orElseThrow(() -> new PaymentMethodNotFoundException("Payment method not found: " + paymentMethodId));

        try {
            // Detach from Stripe if attached
            if (paymentMethod.getStripePaymentMethodId() != null) {
                StripePaymentGateway stripeGateway = gatewayFactory.getStripeGateway();
                stripeGateway.detachPaymentMethod(paymentMethod.getStripePaymentMethodId());
            }

            // Soft delete
            paymentMethod.setIsActive(false);
            paymentMethod.setIsDefault(false);
            paymentMethodRepository.save(paymentMethod);

            log.info("Payment method deleted: {}", paymentMethodId);

        } catch (Exception e) {
            log.error("Failed to delete payment method: {}", paymentMethodId, e);
            throw new PaymentProcessingException("Failed to delete payment method: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentMethodResponse activatePaymentMethod(Long paymentMethodId) {
        PaymentMethod paymentMethod = getPaymentMethodEntityById(paymentMethodId)
                .orElseThrow(() -> new PaymentMethodNotFoundException("Payment method not found: " + paymentMethodId));

        paymentMethod.setIsActive(true);
        paymentMethod = paymentMethodRepository.save(paymentMethod);

        log.info("Payment method activated: {}", paymentMethodId);
        return paymentMethodMapper.toPaymentMethodResponse(paymentMethod);
    }

    @Override
    public PaymentMethodResponse deactivatePaymentMethod(Long paymentMethodId) {
        PaymentMethod paymentMethod = getPaymentMethodEntityById(paymentMethodId)
                .orElseThrow(() -> new PaymentMethodNotFoundException("Payment method not found: " + paymentMethodId));

        paymentMethod.setIsActive(false);
        if (paymentMethod.getIsDefault()) {
            paymentMethod.setIsDefault(false);
        }
        paymentMethod = paymentMethodRepository.save(paymentMethod);

        log.info("Payment method deactivated: {}", paymentMethodId);
        return paymentMethodMapper.toPaymentMethodResponse(paymentMethod);
    }

    // ========== USER PAYMENT METHODS ==========

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getPaymentMethodsByUserId(Long userId) {
        return paymentMethodRepository.findByUserId(userId).stream()
                .map(paymentMethodMapper::toPaymentMethodResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getActivePaymentMethodsByUserId(Long userId) {
        return paymentMethodRepository.findByUserIdAndIsActive(userId, true).stream()
                .map(paymentMethodMapper::toPaymentMethodResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentMethodResponse> getPaymentMethodsByUserId(Long userId, Pageable pageable) {
        return paymentMethodRepository.findByUserId(userId, pageable)
                .map(paymentMethodMapper::toPaymentMethodResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getPaymentMethodsByUserIdAndType(Long userId, PaymentMethodType type) {
        return paymentMethodRepository.findByUserIdAndTypeAndIsActive(userId, type, true).stream()
                .map(paymentMethodMapper::toPaymentMethodResponse)
                .toList();
    }

    // ========== DEFAULT PAYMENT METHOD MANAGEMENT ==========

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentMethodResponse> getDefaultPaymentMethodByUserId(Long userId) {
        return paymentMethodRepository.findDefaultByUserId(userId)
                .map(paymentMethodMapper::toPaymentMethodResponse);
    }

    @Override
    public PaymentMethodResponse setAsDefaultPaymentMethod(Long paymentMethodId) {
        PaymentMethod paymentMethod = getPaymentMethodEntityById(paymentMethodId)
                .orElseThrow(() -> new PaymentMethodNotFoundException("Payment method not found: " + paymentMethodId));

        if (!paymentMethod.getIsActive()) {
            throw new PaymentProcessingException("Cannot set inactive payment method as default");
        }

        // Clear existing default for user
        clearDefaultPaymentMethod(paymentMethod.getUserId());

        // Set as default
        paymentMethod.setIsDefault(true);
        paymentMethod = paymentMethodRepository.save(paymentMethod);

        log.info("Payment method set as default: {}", paymentMethodId);
        return paymentMethodMapper.toPaymentMethodResponse(paymentMethod);
    }

    @Override
    public void clearDefaultPaymentMethod(Long userId) {
        paymentMethodRepository.clearDefaultFlagForUser(userId);
        log.info("Default payment method cleared for user: {}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasDefaultPaymentMethod(Long userId) {
        return paymentMethodRepository.hasDefaultPaymentMethod(userId);
    }

    // ========== HELPER METHODS ==========

    private PaymentMethod createPaymentMethodEntity(CreatePaymentMethodRequest request, StripePaymentMethodResponse stripeResponse) {
        return PaymentMethod.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .provider("STRIPE")
                .stripePaymentMethodId(stripeResponse.getPaymentMethodId())
                .stripeCustomerId(request.getStripeCustomerId())
                .nickname(request.getNickname())
                .isActive(true)
                .isDefault(false)
                .cardBrand(stripeResponse.getCard() != null ? stripeResponse.getCard().getBrand() : null)
                .maskedCardNumber(stripeResponse.getCard() != null ? "**** **** **** " + stripeResponse.getCard().getLast4() : null)
                .expiryMonth(stripeResponse.getCard() != null ? stripeResponse.getCard().getExpMonth() : null)
                .expiryYear(stripeResponse.getCard() != null ? stripeResponse.getCard().getExpYear() : null)
                .customerName(stripeResponse.getBillingDetails() != null ? stripeResponse.getBillingDetails().getName() : null)
                .createdBy("SYSTEM")
                .updatedBy("SYSTEM")
                .build();
    }

    // ========== STRIPE INTEGRATION ==========

    @Override
    public PaymentMethodResponse attachPaymentMethodToCustomer(Long paymentMethodId, String stripeCustomerId) {
        PaymentMethod paymentMethod = getPaymentMethodEntityById(paymentMethodId)
                .orElseThrow(() -> new PaymentMethodNotFoundException("Payment method not found: " + paymentMethodId));

        try {
            StripePaymentGateway stripeGateway = gatewayFactory.getStripeGateway();
            stripeGateway.attachPaymentMethod(paymentMethod.getStripePaymentMethodId(), stripeCustomerId);

            paymentMethod.setStripeCustomerId(stripeCustomerId);
            paymentMethod = paymentMethodRepository.save(paymentMethod);

            log.info("Payment method attached to customer: {} -> {}", paymentMethodId, stripeCustomerId);
            return paymentMethodMapper.toPaymentMethodResponse(paymentMethod);

        } catch (Exception e) {
            log.error("Failed to attach payment method to customer: {}", paymentMethodId, e);
            throw new PaymentProcessingException("Failed to attach payment method: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentMethodResponse detachPaymentMethodFromCustomer(Long paymentMethodId) {
        PaymentMethod paymentMethod = getPaymentMethodEntityById(paymentMethodId)
                .orElseThrow(() -> new PaymentMethodNotFoundException("Payment method not found: " + paymentMethodId));

        try {
            StripePaymentGateway stripeGateway = gatewayFactory.getStripeGateway();
            stripeGateway.detachPaymentMethod(paymentMethod.getStripePaymentMethodId());

            paymentMethod.setStripeCustomerId(null);
            paymentMethod = paymentMethodRepository.save(paymentMethod);

            log.info("Payment method detached from customer: {}", paymentMethodId);
            return paymentMethodMapper.toPaymentMethodResponse(paymentMethod);

        } catch (Exception e) {
            log.error("Failed to detach payment method from customer: {}", paymentMethodId, e);
            throw new PaymentProcessingException("Failed to detach payment method: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentMethodResponse syncPaymentMethodWithStripe(Long paymentMethodId) {
        PaymentMethod paymentMethod = getPaymentMethodEntityById(paymentMethodId)
                .orElseThrow(() -> new PaymentMethodNotFoundException("Payment method not found: " + paymentMethodId));

        if (paymentMethod.getStripePaymentMethodId() == null) {
            throw new PaymentProcessingException("Payment method does not have Stripe payment method ID");
        }

        try {
            StripePaymentGateway stripeGateway = gatewayFactory.getStripeGateway();
            StripePaymentMethodResponse stripeResponse = stripeGateway.getPaymentMethod(paymentMethod.getStripePaymentMethodId());

            // Update payment method with latest Stripe data
            updatePaymentMethodFromStripeResponse(paymentMethod, stripeResponse);
            paymentMethod = paymentMethodRepository.save(paymentMethod);

            log.info("Payment method synced with Stripe: {}", paymentMethodId);
            return paymentMethodMapper.toPaymentMethodResponse(paymentMethod);

        } catch (Exception e) {
            log.error("Failed to sync payment method with Stripe: {}", paymentMethodId, e);
            throw new PaymentProcessingException("Failed to sync payment method: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getStripePaymentMethodsForCustomer(String stripeCustomerId) {
        try {
            StripePaymentGateway stripeGateway = gatewayFactory.getStripeGateway();
            List<StripePaymentMethodResponse> stripePaymentMethods = stripeGateway.listPaymentMethods(stripeCustomerId, null);

            return stripePaymentMethods.stream()
                    .map(this::mapStripeResponseToPaymentMethodResponse)
                    .toList();

        } catch (Exception e) {
            log.error("Failed to get Stripe payment methods for customer: {}", stripeCustomerId, e);
            throw new PaymentProcessingException("Failed to get payment methods: " + e.getMessage(), e);
        }
    }

    // ========== CARD MANAGEMENT ==========

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getCardPaymentMethodsByUserId(Long userId) {
        return paymentMethodRepository.findCardPaymentMethodsByUserId(userId).stream()
                .map(paymentMethodMapper::toPaymentMethodResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getExpiredCardPaymentMethods() {
        YearMonth currentMonth = YearMonth.now();
        return paymentMethodRepository.findExpiredCardPaymentMethods(currentMonth.getYear(), currentMonth.getMonthValue()).stream()
                .map(paymentMethodMapper::toPaymentMethodResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getCardPaymentMethodsExpiringSoon(int months) {
        YearMonth targetMonth = YearMonth.now().plusMonths(months);
        return paymentMethodRepository.findCardPaymentMethodsExpiringSoon(targetMonth.getYear(), targetMonth.getMonthValue()).stream()
                .map(paymentMethodMapper::toPaymentMethodResponse)
                .toList();
    }

    @Override
    public PaymentMethodResponse updateCardExpiry(Long paymentMethodId, Integer expiryMonth, Integer expiryYear) {
        PaymentMethod paymentMethod = getPaymentMethodEntityById(paymentMethodId)
                .orElseThrow(() -> new PaymentMethodNotFoundException("Payment method not found: " + paymentMethodId));

        if (paymentMethod.getType() != PaymentMethodType.CARD) {
            throw new PaymentProcessingException("Payment method is not a card");
        }

        paymentMethod.setExpiryMonth(expiryMonth);
        paymentMethod.setExpiryYear(expiryYear);
        paymentMethod = paymentMethodRepository.save(paymentMethod);

        log.info("Card expiry updated: {}", paymentMethodId);
        return paymentMethodMapper.toPaymentMethodResponse(paymentMethod);
    }

    // ========== PAYMENT METHOD SEARCH ==========

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentMethodResponse> searchPaymentMethods(
            Long userId, PaymentMethodType type, Boolean isActive, Boolean isDefault,
            String provider, String cardBrand, Pageable pageable) {

        return paymentMethodRepository.searchPaymentMethods(
                userId, type, isActive, isDefault, provider, cardBrand, pageable)
                .map(paymentMethodMapper::toPaymentMethodResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getRecentlyUsedPaymentMethods(Long userId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return paymentMethodRepository.findRecentlyUsedByUserId(userId, pageable).stream()
                .map(paymentMethodMapper::toPaymentMethodResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getUnusedPaymentMethods(Long userId) {
        return paymentMethodRepository.findUnusedByUserId(userId).stream()
                .map(paymentMethodMapper::toPaymentMethodResponse)
                .toList();
    }

    // ========== PAYMENT METHOD STATISTICS ==========

    @Override
    @Transactional(readOnly = true)
    public PaymentMethodStatistics getPaymentMethodStatisticsByUserId(Long userId) {
        Object[] stats = paymentMethodRepository.getPaymentMethodStatisticsByUserId(userId);

        Long totalMethods = (Long) stats[0];
        Long activeMethods = (Long) stats[1];
        Long defaultMethods = (Long) stats[2];
        Long cardMethods = (Long) stats[3];
        Long walletMethods = (Long) stats[4];

        // Calculate expired and expiring soon
        YearMonth currentMonth = YearMonth.now();
        Long expiredMethods = (long) paymentMethodRepository.findExpiredCardPaymentMethods(
                currentMonth.getYear(), currentMonth.getMonthValue()).size();

        YearMonth nextMonth = currentMonth.plusMonths(3);
        Long expiringSoonMethods = (long) paymentMethodRepository.findCardPaymentMethodsExpiringSoon(
                nextMonth.getYear(), nextMonth.getMonthValue()).size();

        // Get date range
        List<PaymentMethod> userMethods = paymentMethodRepository.findByUserId(userId);
        LocalDateTime oldestMethodDate = userMethods.stream()
                .map(PaymentMethod::getCreatedAt)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime newestMethodDate = userMethods.stream()
                .map(PaymentMethod::getCreatedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return new PaymentMethodStatistics(
                totalMethods,
                activeMethods,
                defaultMethods,
                cardMethods,
                walletMethods,
                expiredMethods,
                expiringSoonMethods,
                oldestMethodDate,
                newestMethodDate
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Long getPaymentMethodCountByUserAndType(Long userId, PaymentMethodType type) {
        return paymentMethodRepository.countByUserIdAndType(userId, type);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActivePaymentMethods(Long userId) {
        return paymentMethodRepository.hasActivePaymentMethods(userId);
    }

    // ========== UTILITY METHODS ==========

    @Override
    public void updateLastUsedTimestamp(Long paymentMethodId) {
        paymentMethodRepository.updateLastUsedAt(paymentMethodId, LocalDateTime.now());
        log.debug("Updated last used timestamp for payment method: {}", paymentMethodId);
    }

    @Override
    public PaymentMethodResponse updatePaymentMethodNickname(Long paymentMethodId, String nickname) {
        PaymentMethod paymentMethod = getPaymentMethodEntityById(paymentMethodId)
                .orElseThrow(() -> new PaymentMethodNotFoundException("Payment method not found: " + paymentMethodId));

        paymentMethod.setNickname(nickname);
        paymentMethod = paymentMethodRepository.save(paymentMethod);

        log.info("Payment method nickname updated: {}", paymentMethodId);
        return paymentMethodMapper.toPaymentMethodResponse(paymentMethod);
    }

    @Override
    @Transactional(readOnly = true)
    public void validatePaymentMethodOwnership(Long paymentMethodId, Long userId) {
        PaymentMethod paymentMethod = getPaymentMethodEntityById(paymentMethodId)
                .orElseThrow(() -> new PaymentMethodNotFoundException("Payment method not found: " + paymentMethodId));

        if (!paymentMethod.getUserId().equals(userId)) {
            throw new PaymentProcessingException("Payment method does not belong to user");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentMethod> getPaymentMethodEntityById(Long paymentMethodId) {
        return paymentMethodRepository.findById(paymentMethodId);
    }

    @Override
    public PaymentMethod savePaymentMethodEntity(PaymentMethod paymentMethod) {
        return paymentMethodRepository.save(paymentMethod);
    }

    // ========== CLEANUP OPERATIONS ==========

    @Override
    public void cleanupInactivePaymentMethods(LocalDateTime cutoffDate) {
        List<PaymentMethod> inactiveMethods = paymentMethodRepository.findInactivePaymentMethodsForCleanup(cutoffDate);

        for (PaymentMethod method : inactiveMethods) {
            try {
                // Remove from Stripe if needed
                if (method.getStripePaymentMethodId() != null) {
                    StripePaymentGateway stripeGateway = gatewayFactory.getStripeGateway();
                    stripeGateway.detachPaymentMethod(method.getStripePaymentMethodId());
                }

                // Hard delete from database
                paymentMethodRepository.delete(method);
                log.info("Cleaned up inactive payment method: {}", method.getId());

            } catch (Exception e) {
                log.warn("Failed to cleanup payment method: {}", method.getId(), e);
            }
        }
    }

    @Override
    public void cleanupOrphanedPaymentMethods() {
        List<PaymentMethod> orphanedMethods = paymentMethodRepository.findOrphanedPaymentMethods();

        for (PaymentMethod method : orphanedMethods) {
            try {
                paymentMethodRepository.delete(method);
                log.info("Cleaned up orphaned payment method: {}", method.getId());
            } catch (Exception e) {
                log.warn("Failed to cleanup orphaned payment method: {}", method.getId(), e);
            }
        }
    }

    // ========== HELPER METHODS ==========

    private void updatePaymentMethodFromStripeResponse(PaymentMethod paymentMethod, StripePaymentMethodResponse stripeResponse) {
        if (stripeResponse.getCard() != null) {
            paymentMethod.setCardBrand(stripeResponse.getCard().getBrand());
            paymentMethod.setMaskedCardNumber("**** **** **** " + stripeResponse.getCard().getLast4());
            paymentMethod.setExpiryMonth(stripeResponse.getCard().getExpMonth());
            paymentMethod.setExpiryYear(stripeResponse.getCard().getExpYear());
        }

        if (stripeResponse.getBillingDetails() != null) {
            paymentMethod.setCustomerName(stripeResponse.getBillingDetails().getName());
            // Map billing address if available
            if (stripeResponse.getBillingDetails().getAddress() != null) {
                paymentMethod.setBillingAddressLine1(stripeResponse.getBillingDetails().getAddress().getLine1());
                paymentMethod.setBillingAddressLine2(stripeResponse.getBillingDetails().getAddress().getLine2());
                paymentMethod.setBillingCity(stripeResponse.getBillingDetails().getAddress().getCity());
                paymentMethod.setBillingState(stripeResponse.getBillingDetails().getAddress().getState());
                paymentMethod.setBillingPostalCode(stripeResponse.getBillingDetails().getAddress().getPostalCode());
                paymentMethod.setBillingCountry(stripeResponse.getBillingDetails().getAddress().getCountry());
            }
        }

        paymentMethod.setStripeCustomerId(stripeResponse.getCustomerId());
    }

    private PaymentMethodResponse mapStripeResponseToPaymentMethodResponse(StripePaymentMethodResponse stripeResponse) {
        PaymentMethodResponse.PaymentMethodResponseBuilder builder = PaymentMethodResponse.builder()
                .provider("STRIPE")
                .type(PaymentMethodType.valueOf(stripeResponse.getType().toUpperCase()))
                .isActive(true)
                .createdAt(LocalDateTime.now());

        if (stripeResponse.getCard() != null) {
            builder.cardInfo(PaymentMethodResponse.CardInfo.builder()
                    .brand(stripeResponse.getCard().getBrand())
                    .maskedNumber("**** **** **** " + stripeResponse.getCard().getLast4())
                    .expiryMonth(stripeResponse.getCard().getExpMonth())
                    .expiryYear(stripeResponse.getCard().getExpYear())
                    .build());
        }

        if (stripeResponse.getBillingDetails() != null) {
            PaymentMethodResponse.BillingAddress.BillingAddressBuilder billingBuilder =
                    PaymentMethodResponse.BillingAddress.builder()
                            .customerName(stripeResponse.getBillingDetails().getName());

            if (stripeResponse.getBillingDetails().getAddress() != null) {
                billingBuilder
                        .line1(stripeResponse.getBillingDetails().getAddress().getLine1())
                        .line2(stripeResponse.getBillingDetails().getAddress().getLine2())
                        .city(stripeResponse.getBillingDetails().getAddress().getCity())
                        .state(stripeResponse.getBillingDetails().getAddress().getState())
                        .postalCode(stripeResponse.getBillingDetails().getAddress().getPostalCode())
                        .country(stripeResponse.getBillingDetails().getAddress().getCountry());
            }

            builder.billingAddress(billingBuilder.build());
        }

        return builder.build();
    }
}
