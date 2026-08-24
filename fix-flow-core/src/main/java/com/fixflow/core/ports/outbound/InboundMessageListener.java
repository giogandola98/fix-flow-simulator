package com.fixflow.core.ports.outbound;

import com.fixflow.core.domain.execution.FIXMessageData;

import java.util.Map;

/**
 * One abstract method plus one default method is still a functional interface:
 * lambdas that only need the legacy flat-map view keep compiling against the
 * default overload's shape via the abstract SAM's target type.
 */
@FunctionalInterface
public interface InboundMessageListener {

    void onMessage(String sessionId, FIXMessageData message);

    /** Legacy no-group form, for tests and callers that only have a flat map. */
    default void onMessage(String sessionId, Map<Integer, String> fields) {
        onMessage(sessionId, FIXMessageData.ofFields(fields));
    }
}
