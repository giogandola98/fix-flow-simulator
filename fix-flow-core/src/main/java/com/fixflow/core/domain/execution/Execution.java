package com.fixflow.core.domain.execution;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record Execution(
        UUID id,
        UUID scenarioId,
        String scenarioVersion,
        UUID sessionId,
        ExecutionStatus status,
        Instant startTime,
        Instant endTime,
        String currentNodeId,
        Map<String, String> variables,
        List<NodeResult> nodeResults,
        List<ExecutionEvent> events
) {
    public Execution {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        nodeResults = nodeResults == null ? List.of() : List.copyOf(nodeResults);
        events = events == null ? List.of() : List.copyOf(events);
    }
}
