CREATE TABLE compensation_request (
    request_id UUID PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES reconciliation_finding(order_id),
    expected_revision BIGINT NOT NULL CHECK (expected_revision > 0),
    action TEXT NOT NULL CHECK (action = 'VERIFY_EXTERNAL_REFUND'),
    note TEXT NOT NULL CHECK (length(trim(note)) BETWEEN 1 AND 2000),
    actor TEXT NOT NULL,
    db_principal TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    status TEXT NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED','CLAIMED','HOLD','STALE','COMPENSATED')),
    next_check_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    claim_token UUID,
    claim_until TIMESTAMPTZ,
    claim_snapshot JSONB,
    proof_id UUID,
    proof_version BIGINT,
    proof_observed_at TIMESTAMPTZ,
    proof_snapshot JSONB,
    compensated_snapshot JSONB,
    CHECK ((claim_token IS NULL) = (claim_until IS NULL)),
    FOREIGN KEY (order_id, expected_revision) REFERENCES reconciliation_revision(order_id, revision)
);
CREATE UNIQUE INDEX compensation_one_active ON compensation_request(order_id) WHERE status IN ('REQUESTED','CLAIMED');
CREATE INDEX compensation_due ON compensation_request(next_check_at, request_id) WHERE status IN ('REQUESTED','CLAIMED','COMPENSATED');

CREATE TABLE compensation_event (
    id BIGSERIAL PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES compensation_request(request_id),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actor TEXT NOT NULL,
    db_principal TEXT NOT NULL,
    executor_login TEXT NOT NULL,
    action TEXT NOT NULL,
    outcome TEXT NOT NULL,
    before_state JSONB NOT NULL,
    after_state JSONB NOT NULL
);
CREATE TRIGGER compensation_event_immutable BEFORE UPDATE OR DELETE ON compensation_event
    FOR EACH ROW EXECUTE FUNCTION reconciliation_reject_history_mutation();

-- The owner is the migration principal. Grant EXECUTE only to the provisioned operator group.
CREATE FUNCTION compensation_submit(p_request UUID, p_order BIGINT, p_revision BIGINT, p_note TEXT)
RETURNS TEXT LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog AS $$
DECLARE existing public.compensation_request%ROWTYPE;
BEGIN
    IF p_request IS NULL OR p_order IS NULL OR p_revision IS NULL OR p_note IS NULL OR length(trim(p_note)) NOT BETWEEN 1 AND 2000 THEN
        RAISE EXCEPTION 'invalid request' USING ERRCODE = '22023';
    END IF;
    PERFORM 1 FROM public.orders WHERE id = p_order FOR UPDATE;
    PERFORM 1 FROM public.reconciliation_finding WHERE order_id = p_order FOR UPDATE;
    SELECT * INTO existing FROM public.compensation_request WHERE request_id = p_request;
    IF FOUND THEN
        IF existing.order_id <> p_order OR existing.expected_revision <> p_revision OR existing.note <> p_note
                OR existing.actor <> session_user THEN
            RAISE EXCEPTION 'request id conflict' USING ERRCODE = '23505';
        END IF;
        RETURN existing.status;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.reconciliation_finding WHERE order_id = p_order AND current_revision = p_revision AND p_revision > 0) THEN
        RAISE EXCEPTION 'stale revision' USING ERRCODE = '40001';
    END IF;
    INSERT INTO public.compensation_request(request_id, order_id, expected_revision, action, note, actor, db_principal)
        VALUES (p_request, p_order, p_revision, 'VERIFY_EXTERNAL_REFUND', p_note, session_user, current_user);
    INSERT INTO public.compensation_event(request_id, actor, db_principal, executor_login, action, outcome, before_state, after_state)
        VALUES (p_request, session_user, current_user, session_user, 'VERIFY_EXTERNAL_REFUND', 'REQUESTED', '{}'::jsonb,
                jsonb_build_object('orderId', p_order, 'expectedRevision', p_revision));
    RETURN 'REQUESTED';
END;
$$;
REVOKE ALL ON FUNCTION compensation_submit(UUID, BIGINT, BIGINT, TEXT) FROM PUBLIC;
REVOKE ALL ON compensation_request, compensation_event FROM PUBLIC;

-- No remote call is made while holding these locks.
CREATE FUNCTION compensation_local_snapshot(p_order BIGINT) RETURNS JSONB
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog AS $$
DECLARE result JSONB;
BEGIN
    PERFORM 1 FROM public.orders WHERE id=p_order FOR UPDATE;
    PERFORM 1 FROM public.inventory_allocation_intents WHERE order_id=p_order ORDER BY operation_id FOR UPDATE;
    PERFORM 1 FROM public.order_items WHERE order_id=p_order ORDER BY id FOR UPDATE;
    SELECT jsonb_build_object(
        'order', (SELECT jsonb_build_object('id',o.id,'userId',o.user_id,'status',o.status,'allocationPhase',o.allocation_phase,
            'acceptedPaymentId',o.accepted_payment_id,'acceptedPaymentNumber',o.accepted_payment_number,
            'amount',o.total_amount,'currency',o.currency,'checkoutKey',o.checkout_key,'deadline',o.checkout_deadline,
            'closeOperation',o.checkout_close_operation,'closeSafeAt',o.checkout_close_safe_at,
            'actualDeliveryDate',o.actual_delivery_date,'deletedAt',o.deleted_at) FROM public.orders o WHERE o.id=p_order),
        'intents', (SELECT coalesce(jsonb_agg(jsonb_build_object('operationId',i.operation_id,'targetKind',i.target_kind,
            'targetId',i.target_id,'quantity',i.quantity,'owner',i.owner_id,'state',i.state) ORDER BY i.operation_id), '[]'::jsonb)
            FROM public.inventory_allocation_intents i WHERE i.order_id=p_order),
        'items', (SELECT coalesce(jsonb_agg(jsonb_build_object('id',i.id,'productId',i.product_id,'quantity',i.quantity,
            'status',i.status,'actualDeliveryDate',i.actual_delivery_date,'deletedAt',i.deleted_at) ORDER BY i.id), '[]'::jsonb)
            FROM public.order_items i WHERE i.order_id=p_order),
        'checkout', (SELECT jsonb_build_object('userId',a.user_id,'requestSnapshotHash',encode(sha256(convert_to(a.request_snapshot,'UTF8')),'hex'),
            'requestUserId', CASE WHEN a.request_snapshot IS JSON OBJECT THEN a.request_snapshot::jsonb->>'userId' END,
            'requestCurrency', CASE WHEN a.request_snapshot IS JSON OBJECT THEN a.request_snapshot::jsonb->>'currency' END,
            'requestPaymentMethod', CASE WHEN a.request_snapshot IS JSON OBJECT THEN a.request_snapshot::jsonb->>'paymentMethod' END)
            FROM public.checkout_attempts a JOIN public.orders o ON o.checkout_key=a.attempt_key WHERE o.id=p_order),
        -- Inbox has no order id; do not infer an absence of accepted events from it.
        'acceptedEventHistoryAvailable', false
    ) INTO result;
    RETURN result;
END;
$$;
REVOKE ALL ON FUNCTION compensation_local_snapshot(BIGINT) FROM PUBLIC;

CREATE FUNCTION compensation_claim(p_lease_ms BIGINT)
RETURNS TABLE(request_id UUID, order_id BIGINT, expected_revision BIGINT, claim_token UUID, local_snapshot JSONB, recurrence BOOLEAN, proof_snapshot JSONB, compensated_snapshot JSONB)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog AS $$
DECLARE r public.compensation_request%ROWTYPE; candidate RECORD; current_rev BIGINT; current_snapshot JSONB;
BEGIN
    IF p_lease_ms IS NULL OR p_lease_ms <= 0 OR p_lease_ms > 900000 THEN RAISE EXCEPTION 'invalid lease'; END IF;
    -- Lock order first, matching detection; skip busy orders rather than blocking the due queue.
    SELECT q.request_id, q.order_id INTO candidate FROM public.compensation_request q
      JOIN public.orders o ON o.id=q.order_id
      WHERE q.status IN ('REQUESTED','CLAIMED','COMPENSATED') AND q.next_check_at <= clock_timestamp()
        AND (q.claim_until IS NULL OR q.claim_until <= clock_timestamp())
      ORDER BY q.next_check_at, q.request_id FOR UPDATE OF o SKIP LOCKED LIMIT 1;
    IF NOT FOUND THEN RETURN; END IF;
    SELECT f.current_revision INTO current_rev FROM public.reconciliation_finding f WHERE f.order_id=candidate.order_id FOR UPDATE;
    SELECT * INTO r FROM public.compensation_request q WHERE q.request_id=candidate.request_id
      AND q.status IN ('REQUESTED','CLAIMED','COMPENSATED') AND q.next_check_at <= clock_timestamp()
      AND (q.claim_until IS NULL OR q.claim_until <= clock_timestamp()) FOR UPDATE;
    IF NOT FOUND THEN RETURN; END IF;
    IF r.proof_id IS NULL AND current_rev IS DISTINCT FROM r.expected_revision THEN
      current_snapshot := public.compensation_local_snapshot(r.order_id);
      UPDATE public.compensation_request q SET status='STALE',claim_token=NULL,claim_until=NULL,claim_snapshot=NULL
        WHERE q.request_id=r.request_id AND q.status IN ('REQUESTED','CLAIMED')
          AND (q.claim_until IS NULL OR q.claim_until <= clock_timestamp());
      IF FOUND THEN
        INSERT INTO public.compensation_event(request_id,actor,db_principal,executor_login,action,outcome,before_state,after_state)
          VALUES(r.request_id,r.actor,current_user,session_user,'REVISION_CHANGED','STALE',coalesce(r.claim_snapshot,'{}'::jsonb),current_snapshot);
      END IF;
      RETURN;
    END IF;
    UPDATE public.compensation_request q SET status=CASE WHEN r.proof_id IS NULL THEN 'CLAIMED' ELSE 'COMPENSATED' END, claim_token=gen_random_uuid(),
      claim_until=clock_timestamp() + p_lease_ms * interval '1 millisecond',
      next_check_at=clock_timestamp() + p_lease_ms * interval '1 millisecond',
      claim_snapshot=public.compensation_local_snapshot(r.order_id)
      WHERE q.request_id=r.request_id RETURNING * INTO r;
    RETURN QUERY SELECT r.request_id,r.order_id,r.expected_revision,r.claim_token,r.claim_snapshot,r.proof_id IS NOT NULL,r.proof_snapshot,r.compensated_snapshot;
END;
$$;
REVOKE ALL ON FUNCTION compensation_claim(BIGINT) FROM PUBLIC;

CREATE FUNCTION compensation_hold(p_request UUID, p_token UUID, p_reason TEXT) RETURNS BOOLEAN
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog AS $$
DECLARE r public.compensation_request%ROWTYPE; current_snapshot JSONB; revision BIGINT; result TEXT; candidate_order BIGINT;
BEGIN
    IF p_reason IS NULL OR length(trim(p_reason)) NOT BETWEEN 1 AND 500 THEN RAISE EXCEPTION 'invalid hold'; END IF;
    SELECT order_id INTO candidate_order FROM public.compensation_request WHERE request_id=p_request;
    IF NOT FOUND THEN RETURN false; END IF;
    PERFORM 1 FROM public.orders WHERE id=candidate_order FOR UPDATE;
    SELECT current_revision INTO revision FROM public.reconciliation_finding WHERE order_id=candidate_order FOR UPDATE;
    SELECT * INTO r FROM public.compensation_request WHERE request_id=p_request FOR UPDATE;
    IF NOT FOUND OR r.order_id <> candidate_order OR r.status <> 'CLAIMED' OR r.claim_token IS DISTINCT FROM p_token OR r.claim_until <= clock_timestamp() THEN RETURN false; END IF;
    current_snapshot := public.compensation_local_snapshot(r.order_id);
    result := CASE WHEN revision IS DISTINCT FROM r.expected_revision OR r.claim_snapshot IS NULL OR current_snapshot IS DISTINCT FROM r.claim_snapshot THEN 'STALE' ELSE 'HOLD' END;
    UPDATE public.compensation_request SET status=result, claim_token=NULL, claim_until=NULL, claim_snapshot=NULL WHERE request_id=p_request AND claim_token=p_token AND claim_until > clock_timestamp();
    IF NOT FOUND THEN RETURN false; END IF;
    INSERT INTO public.compensation_event(request_id,actor,db_principal,executor_login,action,outcome,before_state,after_state)
      VALUES (p_request, r.actor, current_user, session_user, p_reason, result, coalesce(r.claim_snapshot,'{}'::jsonb), current_snapshot);
    RETURN true;
END;
$$;
REVOKE ALL ON FUNCTION compensation_hold(UUID, UUID, TEXT) FROM PUBLIC;

CREATE FUNCTION compensation_retry(p_request UUID, p_token UUID, p_delay_ms BIGINT) RETURNS BOOLEAN
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog AS $$
BEGIN
    IF p_delay_ms <= 0 OR p_delay_ms > 3600000 THEN RAISE EXCEPTION 'invalid delay'; END IF;
    UPDATE public.compensation_request SET status='REQUESTED', claim_token=NULL, claim_until=NULL, claim_snapshot=NULL,
      next_check_at=clock_timestamp()+p_delay_ms*interval '1 millisecond'
      WHERE request_id=p_request AND status='CLAIMED' AND claim_token=p_token AND claim_until>clock_timestamp();
    RETURN FOUND;
END;
$$;
REVOKE ALL ON FUNCTION compensation_retry(UUID, UUID, BIGINT) FROM PUBLIC;

-- The resolver is trusted to validate Payment and Catalog observations; these are not cryptographic DB proofs.
CREATE FUNCTION compensation_finalize(p_request UUID, p_token UUID, p_proof JSONB, p_updates JSONB) RETURNS BOOLEAN
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog AS $$
DECLARE r public.compensation_request%ROWTYPE; oid BIGINT; rev BIGINT; before_snapshot JSONB; after_snapshot JSONB; entry JSONB; changed INT;
BEGIN
    SELECT order_id INTO oid FROM public.compensation_request WHERE request_id=p_request;
    IF NOT FOUND THEN RETURN false; END IF;
    PERFORM 1 FROM public.orders WHERE id=oid FOR UPDATE;
    SELECT current_revision INTO rev FROM public.reconciliation_finding WHERE order_id=oid FOR UPDATE;
    SELECT * INTO r FROM public.compensation_request WHERE request_id=p_request FOR UPDATE;
    IF NOT FOUND OR r.status <> 'CLAIMED' OR r.claim_token IS DISTINCT FROM p_token OR r.claim_until <= clock_timestamp() THEN RETURN false; END IF;
    before_snapshot := public.compensation_local_snapshot(oid);
    IF rev IS DISTINCT FROM r.expected_revision OR before_snapshot IS DISTINCT FROM r.claim_snapshot THEN
      UPDATE public.compensation_request SET status='STALE',claim_token=NULL,claim_until=NULL,claim_snapshot=NULL
        WHERE request_id=p_request AND status='CLAIMED' AND claim_token=p_token AND claim_until>clock_timestamp();
      IF NOT FOUND THEN RETURN false; END IF;
      INSERT INTO public.compensation_event(request_id,actor,db_principal,executor_login,action,outcome,before_state,after_state)
        VALUES(p_request,r.actor,current_user,session_user,'FACTS_CHANGED','STALE',coalesce(r.claim_snapshot,'{}'::jsonb),before_snapshot);
      RETURN false;
    END IF;
    IF p_proof->>'status' IS DISTINCT FROM 'COMPENSATED' OR (p_proof->>'orderId')::bigint IS DISTINCT FROM oid
       OR (p_proof->>'operationId')::uuid IS DISTINCT FROM p_request OR (p_proof->>'findingRevision')::bigint IS DISTINCT FROM rev
       OR p_proof->>'proofId' IS NULL OR p_proof->>'observedAt' IS NULL OR p_proof->>'proofVersion' IS NULL
       OR (p_proof->>'proofVersion')::bigint IS DISTINCT FROM (p_proof->>'financialVersion')::bigint
       OR (p_proof->>'closeOperationId') IS DISTINCT FROM (before_snapshot->'order'->>'closeOperation')
       OR (p_proof->>'amount')::numeric IS DISTINCT FROM (before_snapshot->'order'->>'amount')::numeric
       OR (p_proof->>'currency') IS DISTINCT FROM (before_snapshot->'order'->>'currency')
       OR p_proof->>'paymentId' IS NULL OR p_proof->>'paymentNumber' IS NULL OR jsonb_typeof(p_updates) IS DISTINCT FROM 'array'
       THEN RETURN false; END IF;
    FOR entry IN SELECT * FROM jsonb_array_elements(p_updates) LOOP
      IF NOT ((entry->>'before'='PENDING' AND entry->>'after' IN ('RELEASED','REJECTED')) OR
              (entry->>'before'='RESERVED' AND entry->>'after'='RELEASED')) THEN RAISE EXCEPTION 'invalid transition'; END IF;
      UPDATE public.inventory_allocation_intents SET state=entry->>'after' WHERE order_id=oid
        AND operation_id=(entry->>'operationId')::uuid AND state=entry->>'before';
      GET DIAGNOSTICS changed = ROW_COUNT;
      IF changed <> 1 THEN RAISE EXCEPTION 'intent changed'; END IF;
    END LOOP;
    IF r.claim_until <= clock_timestamp() THEN RAISE EXCEPTION 'claim expired'; END IF;
    after_snapshot := public.compensation_local_snapshot(oid);
    UPDATE public.compensation_request SET status='COMPENSATED',proof_id=(p_proof->>'proofId')::uuid,
      proof_version=(p_proof->>'proofVersion')::bigint,proof_observed_at=(p_proof->>'observedAt')::timestamptz,
      proof_snapshot=p_proof - 'evidence', compensated_snapshot=after_snapshot,
      claim_token=NULL,claim_until=NULL,claim_snapshot=NULL,next_check_at=clock_timestamp()+interval '5 minutes' WHERE request_id=p_request;
    INSERT INTO public.compensation_event(request_id,actor,db_principal,executor_login,action,outcome,before_state,after_state)
      VALUES (p_request,r.actor,current_user,session_user,'VERIFY_EXTERNAL_REFUND','COMPENSATED',before_snapshot,
          jsonb_build_object('local',after_snapshot,'proofId',p_proof->>'proofId','proofVersion',(p_proof->>'proofVersion')::bigint,'observedAt',p_proof->>'observedAt'));
    RETURN true;
END;
$$;
REVOKE ALL ON FUNCTION compensation_finalize(UUID, UUID, JSONB, JSONB) FROM PUBLIC;

CREATE FUNCTION compensation_recurrence(p_request UUID,p_token UUID,p_reason TEXT,p_delay_ms BIGINT) RETURNS BOOLEAN
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE r public.compensation_request%ROWTYPE; oid BIGINT; current_snapshot JSONB; outcome TEXT;
BEGIN
    IF p_delay_ms IS NULL OR p_delay_ms <= 0 OR p_delay_ms > 3600000 THEN RAISE EXCEPTION 'invalid delay'; END IF;
    SELECT order_id INTO oid FROM public.compensation_request WHERE request_id=p_request;
    IF NOT FOUND THEN RETURN false; END IF;
    PERFORM 1 FROM public.orders WHERE id=oid FOR UPDATE;
    PERFORM 1 FROM public.reconciliation_finding WHERE order_id=oid FOR UPDATE;
    SELECT * INTO r FROM public.compensation_request WHERE request_id=p_request FOR UPDATE;
    IF NOT FOUND OR r.status <> 'COMPENSATED' OR r.claim_token IS DISTINCT FROM p_token OR r.claim_until <= clock_timestamp() THEN RETURN false; END IF;
    current_snapshot := public.compensation_local_snapshot(oid);
    outcome := CASE WHEN p_reason IS NOT NULL OR current_snapshot IS DISTINCT FROM r.compensated_snapshot OR current_snapshot IS DISTINCT FROM r.claim_snapshot THEN 'HOLD' ELSE 'COMPENSATED' END;
    UPDATE public.compensation_request SET status=outcome,claim_token=NULL,claim_until=NULL,claim_snapshot=NULL,
      next_check_at=clock_timestamp()+p_delay_ms*interval '1 millisecond' WHERE request_id=p_request AND claim_token=p_token AND claim_until>clock_timestamp();
    IF NOT FOUND THEN RETURN false; END IF;
    IF outcome='HOLD' THEN
      INSERT INTO public.compensation_event(request_id,actor,db_principal,executor_login,action,outcome,before_state,after_state)
      VALUES(p_request,r.actor,current_user,session_user,coalesce(p_reason,'LOCAL_FACTS_CHANGED'),'HOLD',r.compensated_snapshot,current_snapshot);
    END IF;
    RETURN true;
END;
$$;
REVOKE ALL ON FUNCTION compensation_recurrence(UUID,UUID,TEXT,BIGINT) FROM PUBLIC;
