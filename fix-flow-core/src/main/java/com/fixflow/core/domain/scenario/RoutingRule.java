package com.fixflow.core.domain.scenario;

import java.util.Map;

public record RoutingRule(Map<String, String> criteria, String scenarioId, int priority) {
    public RoutingRule {
        criteria = criteria == null ? Map.of() : Map.copyOf(criteria);
    }
}
