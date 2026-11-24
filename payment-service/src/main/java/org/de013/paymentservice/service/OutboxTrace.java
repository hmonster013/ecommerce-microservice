package org.de013.paymentservice.service;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.HashMap;
import java.util.Map;

final class OutboxTrace {
    private static final W3CTraceContextPropagator PROPAGATOR = W3CTraceContextPropagator.getInstance();
    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
        public Iterable<String> keys(Map<String, String> carrier) { return carrier.keySet(); }
        public String get(Map<String, String> carrier, String key) { return carrier.get(key); }
    };

    static Map<String, String> capture() {
        Map<String, String> carrier = new HashMap<>();
        PROPAGATOR.inject(Context.current(), carrier, Map::put);
        return validated(carrier.get("traceparent"), carrier.get("tracestate"));
    }

    static Context restore(String parent, String state) {
        return PROPAGATOR.extract(Context.root(), validated(parent, state), GETTER);
    }

    private static Map<String, String> validated(String parent, String state) {
        Map<String, String> carrier = new HashMap<>();
        if (parent == null || !parent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")) return carrier;
        carrier.put("traceparent", parent);
        if (state != null && state.length() <= 512) carrier.put("tracestate", state);
        Context parsed = PROPAGATOR.extract(Context.root(), carrier, GETTER);
        Map<String, String> clean = new HashMap<>();
        PROPAGATOR.inject(parsed, clean, Map::put);
        return clean;
    }

    private OutboxTrace() { }
}
