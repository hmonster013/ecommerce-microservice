package org.de013.paymentservice.entity.enums;

/**
 * Payment method types supported by Stripe
 */
public enum PaymentMethodType {
    /**
     * Credit/Debit card
     */
    CARD,
    
    /**
     * Bank account (ACH, SEPA, etc.)
     */
    BANK_ACCOUNT,
    
    /**
     * Digital wallet (Apple Pay, Google Pay)
     */
    WALLET,
    
    /**
     * Buy now, pay later (Klarna, Afterpay)
     */
    BUY_NOW_PAY_LATER,
    
    /**
     * Bank transfer
     */
    BANK_TRANSFER,
    
    /**
     * Other payment methods
     */
    OTHER
}
