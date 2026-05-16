package com.fixflow.core.domain.scenario;

import java.util.Map;

public record ScenarioNode(
        String id,
        String name,
        NodeType type,
        Map<String, Object> config,
        TimeoutConfig timeout,
        RetryPolicy retryPolicy,
        String onSuccess,
        String onFailure,
        String onTimeout
) {
    public ScenarioNode {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("node id required");
        if (type == null) throw new IllegalArgumentException("node type required");
        config = config == null ? Map.of() : Map.copyOf(config);
    }
}
