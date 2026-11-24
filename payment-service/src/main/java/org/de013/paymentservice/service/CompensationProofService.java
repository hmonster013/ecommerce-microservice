package org.de013.paymentservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompensationProofService {
    private final CompensationProofStore store;
    private final StripeCompensationVerifier verifier;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final String account;

    public CompensationProofService(CompensationProofStore store, StripeCompensationVerifier verifier, ObjectMapper mapper,
            @Value("${payment.compensation.enabled:false}") boolean enabled,
            @Value("${payment.compensation.stripe.expected-account-id:}") String account) {
        this.store = store; this.verifier = verifier; this.mapper = mapper; this.enabled = enabled; this.account = account;
    }

    @Transactional(propagation = Propagation.NEVER)
    public CompensationProofStore.View resolve(long orderId, CompensationProofStore.Request request) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Compensation disabled");
        var claim = store.begin(orderId, request);
        if (claim.token() == null) return claim.view();
        StripeCompensationVerifier.Result result;
        try {
            JsonNode root = mapper.readTree(claim.snapshot());
            if (!root.path("payments").isArray() || !root.path("hosted").isArray() || !root.path("retry").isArray()
                    || !root.path("hosted").isEmpty() || !root.path("retry").isEmpty()) throw new IllegalArgumentException("Unsupported snapshot");
            var attempts = new ArrayList<StripeCompensationVerifier.Attempt>();
            for (JsonNode p : root.path("payments")) {
                if (!p.isArray() || p.size() != 12 || !p.get(0).canConvertToLong() || !p.get(3).isNumber()
                        || !p.get(8).isBoolean() || !p.get(9).isBoolean()) throw new IllegalArgumentException("Incomplete attempt");
                attempts.add(new StripeCompensationVerifier.Attempt(p.get(0).longValue(), text(p.get(2)), text(p.get(7)),
                        p.get(8).booleanValue(), p.get(9).booleanValue(), p.get(3).decimalValue(), text(p.get(4)), false, text(p.get(5))));
            }
            result = verifier.verify(account, attempts);
        } catch (Exception e) {
            result = new StripeCompensationVerifier.Result(false, "UNSUPPORTED_SNAPSHOT", null);
        }
        return store.complete(orderId, request.operationId(), claim.token(), result);
    }

    private static String text(JsonNode value) {
        return value != null && value.isTextual() ? value.textValue() : null;
    }
}
