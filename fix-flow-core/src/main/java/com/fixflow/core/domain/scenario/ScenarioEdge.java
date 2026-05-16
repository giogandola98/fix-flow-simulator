package com.fixflow.core.domain.scenario;

public record ScenarioEdge(String from, String to, String label) {
    public ScenarioEdge {
        if (from == null || from.isBlank()) throw new IllegalArgumentException("edge from required");
        if (to == null || to.isBlank()) throw new IllegalArgumentException("edge to required");
    }
}
