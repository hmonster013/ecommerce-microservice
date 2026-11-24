ALTER TABLE payment_order_guard
    ADD COLUMN permanent_fence boolean NOT NULL DEFAULT false,
    ADD COLUMN financial_version bigint NOT NULL DEFAULT 0 CHECK (financial_version >= 0);

CREATE TABLE stripe_financial_observation (
    event_id varchar(255) PRIMARY KEY,
    event_type varchar(80) NOT NULL,
    object_id varchar(255),
    intent_id varchar(255),
    charge_id varchar(255),
    status varchar(80),
    amount bigint,
    currency varchar(16),
    livemode boolean,
    account_id varchar(255),
    order_id bigint
);

CREATE INDEX stripe_financial_observation_unresolved ON stripe_financial_observation (event_id) WHERE order_id IS NULL;
