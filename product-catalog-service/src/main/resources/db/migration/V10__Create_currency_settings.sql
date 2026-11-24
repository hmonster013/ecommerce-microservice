CREATE TABLE currency_settings (
    id BIGINT PRIMARY KEY,
    base_currency VARCHAR(3) NOT NULL,
    version BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255),
    CONSTRAINT chk_currency_settings_singleton CHECK (id = 1),
    CONSTRAINT chk_currency_settings_base CHECK (base_currency = 'USD')
);

CREATE TABLE currency_exchange_rates (
    currency_code VARCHAR(3) PRIMARY KEY,
    settings_id BIGINT NOT NULL REFERENCES currency_settings(id),
    rate NUMERIC(24, 10) NOT NULL,
    enabled BOOLEAN NOT NULL,
    CONSTRAINT chk_currency_exchange_rate_positive CHECK (rate > 0)
);

INSERT INTO currency_settings (id, base_currency, version) VALUES (1, 'USD', 0);
INSERT INTO currency_exchange_rates (currency_code, settings_id, rate, enabled) VALUES ('USD', 1, 1, true);
