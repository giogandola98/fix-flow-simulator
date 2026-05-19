package com.fixflow.core.domain.scenario;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record Scenario(
        UUID id,
        String name,
        String description,
        String version,
        String sessionRef,
        RuntimePolicy runtimePolicy,
        List<RoutingRule> routingRules,
        List<CorrelationRule> correlationRules,
        List<ScenarioNode> nodes,
        List<ScenarioEdge> edges,
        Map<String, VariableDef> variables,
        String rawYaml
) {
    public Scenario {
        if (id == null) throw new IllegalArgumentException("scenario id required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("scenario name required");
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        routingRules = routingRules == null ? List.of() : List.copyOf(routingRules);
        correlationRules = correlationRules == null ? List.of() : List.copyOf(correlationRules);
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    public Optional<ScenarioNode> findNode(String nodeId) {
        if (nodeId == null) return Optional.empty();
        return nodes.stream().filter(n -> nodeId.equals(n.id())).findFirst();
    }

    public Optional<ScenarioNode> startNode() {
        return nodes.stream().filter(n -> n.type() == NodeType.START).findFirst();
    }
}
