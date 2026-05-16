package com.fixflow.api.rest.dto;

import com.fixflow.core.domain.execution.Execution;
import java.time.Instant;
import java.util.UUID;

public record ExecutionDto(
    UUID id, UUID scenarioId, String scenarioVersion, UUID sessionId,
    String status, Instant startTime, Instant endTime, String currentNodeId
) {
    public static ExecutionDto from(Execution e) {
        return new ExecutionDto(
            e.id(), e.scenarioId(), e.scenarioVersion(), e.sessionId(),
            e.status().name(), e.startTime(), e.endTime(), e.currentNodeId()
        );
    }
}
