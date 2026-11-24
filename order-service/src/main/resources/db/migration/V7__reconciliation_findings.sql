CREATE TABLE reconciliation_finding (
    order_id BIGINT PRIMARY KEY REFERENCES orders(id),
    current_revision BIGINT NOT NULL DEFAULT 0 CHECK (current_revision >= 0),
    next_check_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    claim_token UUID,
    claim_until TIMESTAMPTZ,
    CHECK ((claim_token IS NULL) = (claim_until IS NULL))
);
CREATE INDEX idx_reconciliation_due ON reconciliation_finding(next_check_at, order_id);
CREATE TABLE reconciliation_cursor (id INTEGER PRIMARY KEY CHECK (id = 1), last_order_id BIGINT NOT NULL);
INSERT INTO reconciliation_cursor(id, last_order_id) VALUES (1, 0);

CREATE TABLE reconciliation_revision (
    order_id BIGINT NOT NULL REFERENCES reconciliation_finding(order_id),
    revision BIGINT NOT NULL CHECK (revision > 0),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    evidence JSONB NOT NULL,
    reasons JSONB NOT NULL CHECK (jsonb_typeof(reasons) = 'array'),
    PRIMARY KEY (order_id, revision)
);

CREATE TABLE reconciliation_audit (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    db_principal TEXT NOT NULL DEFAULT current_user,
    actor TEXT NOT NULL CHECK (length(trim(actor)) > 0),
    note TEXT NOT NULL CHECK (length(trim(note)) > 0),
    action TEXT NOT NULL CHECK (action IN ('ACKNOWLEDGED', 'INVESTIGATING', 'ESCALATED')),
    FOREIGN KEY (order_id, revision) REFERENCES reconciliation_revision(order_id, revision)
);
CREATE FUNCTION reconciliation_reject_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'reconciliation history is append-only';
END;
$$;
CREATE TRIGGER reconciliation_audit_immutable BEFORE UPDATE OR DELETE ON reconciliation_audit
    FOR EACH ROW EXECUTE FUNCTION reconciliation_reject_history_mutation();
CREATE TRIGGER reconciliation_revision_immutable BEFORE UPDATE OR DELETE ON reconciliation_revision
    FOR EACH ROW EXECUTE FUNCTION reconciliation_reject_history_mutation();
