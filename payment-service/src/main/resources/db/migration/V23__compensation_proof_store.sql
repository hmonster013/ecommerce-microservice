CREATE TABLE compensation_operation (
    order_id bigint PRIMARY KEY REFERENCES payment_order_guard(order_id),
    operation_id uuid NOT NULL UNIQUE,
    finding_revision bigint NOT NULL,
    expected_payment_id bigint NOT NULL,
    expected_payment_number varchar(100) NOT NULL,
    expected_amount numeric(19,2) NOT NULL,
    expected_currency varchar(3) NOT NULL,
    expected_close_operation_id uuid,
    status varchar(16) NOT NULL CHECK (status IN ('VERIFYING', 'COMPENSATED', 'HOLD')),
    reason varchar(100),
    claim_token uuid,
    claim_until timestamptz,
    snapshot_version bigint NOT NULL,
    snapshot jsonb NOT NULL,
    proof_id uuid,
    CHECK ((status = 'VERIFYING' AND claim_token IS NOT NULL AND claim_until IS NOT NULL)
        OR (status <> 'VERIFYING' AND claim_token IS NULL AND claim_until IS NULL))
);

CREATE TABLE compensation_proof (
    proof_id uuid PRIMARY KEY,
    operation_id uuid NOT NULL REFERENCES compensation_operation(operation_id),
    proof_version bigint NOT NULL,
    observed_at timestamptz NOT NULL,
    evidence jsonb NOT NULL
);

CREATE TABLE compensation_history (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    operation_id uuid NOT NULL REFERENCES compensation_operation(operation_id),
    status varchar(16) NOT NULL,
    reason varchar(100),
    financial_version bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE FUNCTION compensation_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'compensation history is immutable';
END;
$$;
CREATE TRIGGER compensation_proof_immutable BEFORE UPDATE OR DELETE ON compensation_proof FOR EACH ROW EXECUTE FUNCTION compensation_immutable();
CREATE TRIGGER compensation_history_immutable BEFORE UPDATE OR DELETE ON compensation_history FOR EACH ROW EXECUTE FUNCTION compensation_immutable();

CREATE FUNCTION compensation_payload_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(OLD.order_id, OLD.operation_id, OLD.finding_revision, OLD.expected_payment_id, OLD.expected_payment_number, OLD.expected_amount, OLD.expected_currency, OLD.expected_close_operation_id)
       IS DISTINCT FROM ROW(NEW.order_id, NEW.operation_id, NEW.finding_revision, NEW.expected_payment_id, NEW.expected_payment_number, NEW.expected_amount, NEW.expected_currency, NEW.expected_close_operation_id) THEN
        RAISE EXCEPTION 'compensation operation payload is immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER compensation_payload_immutable BEFORE UPDATE ON compensation_operation FOR EACH ROW EXECUTE FUNCTION compensation_payload_immutable();
