package com.fixflow.core.ports.outbound;

import java.util.Map;
import java.util.UUID;

@FunctionalInterface
public interface InboundMessageListener {
    void onMessage(UUID sessionId, Map<Integer, String> fields);
}
