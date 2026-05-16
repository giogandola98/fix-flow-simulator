package com.fixflow.core.ports.outbound;

import java.util.Map;

@FunctionalInterface
public interface InboundMessageListener {
    void onMessage(String sessionId, Map<Integer, String> fields);
}
