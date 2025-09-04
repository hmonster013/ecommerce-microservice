package org.de013.paymentservice.exception;

import java.math.BigDecimal;

/**
 * Exception thrown when there are insufficient funds for a payment operation.
 * This can occur during payment processing or refund operations.
 */
public class InsufficientFundsException extends PaymentProcessingException {
    
    private final BigDecimal requestedAmount;
    private final BigDecimal availableAmount;
    private final String currency;
    private final String accountType;
    
    public InsufficientFundsException(String message) {
        super(message);
        this.requestedAmount = null;
        this.availableAmount = null;
        this.currency = null;
        this.accountType = null;
    }
    
    public InsufficientFundsException(String message, Throwable cause) {
        super(message, cause);
        this.requestedAmount = null;
        this.availableAmount = null;
        this.currency = null;
        this.accountType = null;
    }
    
    public InsufficientFundsException(String message, BigDecimal requestedAmount, 
                                    BigDecimal availableAmount, String currency) {
        super(message);
        this.requestedAmount = requestedAmount;
        this.availableAmount = availableAmount;
        this.currency = currency;
        this.accountType = null;
    }
    
    public InsufficientFundsException(String message, BigDecimal requestedAmount, 
                                    BigDecimal availableAmount, String currency, String accountType) {
        super(message);
        this.requestedAmount = requestedAmount;
        this.availableAmount = availableAmount;
        this.currency = currency;
        this.accountType = accountType;
    }
    
    public InsufficientFundsException(String message, String paymentId, String operation,
                                    BigDecimal requestedAmount, BigDecimal availableAmount, String currency) {
        super(message, paymentId, operation);
        this.requestedAmount = requestedAmount;
        this.availableAmount = availableAmount;
        this.currency = currency;
        this.accountType = null;
    }
    
    // Getters
    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }
    
    public BigDecimal getAvailableAmount() {
        return availableAmount;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public String getAccountType() {
        return accountType;
    }
    
    // Static factory methods
    public static InsufficientFundsException forPayment(String paymentId, BigDecimal requestedAmount, 
                                                       BigDecimal availableAmount, String currency) {
        String message = String.format(
            "Insufficient funds for payment. Requested: %s %s, Available: %s %s",
            requestedAmount, currency, availableAmount, currency
        );
        return new InsufficientFundsException(
            message, paymentId, "PROCESS", requestedAmount, availableAmount, currency
        );
    }
    
    public static InsufficientFundsException forRefund(String paymentId, BigDecimal requestedAmount, 
                                                      BigDecimal availableAmount, String currency) {
        String message = String.format(
            "Insufficient funds for refund. Requested: %s %s, Available: %s %s",
            requestedAmount, currency, availableAmount, currency
        );
        return new InsufficientFundsException(
            message, paymentId, "REFUND", requestedAmount, availableAmount, currency
        );
    }
    
    public static InsufficientFundsException forCard(String paymentMethodId, BigDecimal requestedAmount, String currency) {
        String message = String.format(
            "Insufficient funds on card for amount: %s %s",
            requestedAmount, currency
        );
        return new InsufficientFundsException(
            message, paymentMethodId, "VALIDATE", requestedAmount, null, currency
        );
    }
    
    public static InsufficientFundsException forAccount(String accountId, BigDecimal requestedAmount, 
                                                       BigDecimal availableAmount, String currency, String accountType) {
        String message = String.format(
            "Insufficient funds in %s account. Requested: %s %s, Available: %s %s",
            accountType, requestedAmount, currency, availableAmount, currency
        );
        InsufficientFundsException exception = new InsufficientFundsException(
            message, requestedAmount, availableAmount, currency, accountType
        );
        return exception;
    }
    
    public static InsufficientFundsException generic(BigDecimal requestedAmount, String currency) {
        String message = String.format(
            "Insufficient funds for requested amount: %s %s",
            requestedAmount, currency
        );
        return new InsufficientFundsException(message, requestedAmount, null, currency);
    }
}
