package org.de013.productcatalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "currency_exchange_rates")
@Getter
@Setter
@NoArgsConstructor
public class CurrencyExchangeRate {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settings_id", nullable = false)
    private CurrencySettings settings;

    @Id
    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(nullable = false, precision = 24, scale = 10)
    private BigDecimal rate;

    @Column(nullable = false)
    private boolean enabled;
}
