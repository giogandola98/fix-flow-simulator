package com.fixflow.core.domain.execution;

import java.time.Instant;
import java.util.UUID;

public record ExecutionEvent(
        UUID id,
        UUID executionId,
        ExecutionEventType type,
        String nodeId,
        Instant timestamp,
        String detail,
        String rawFix
) {
    public static ExecutionEvent of(UUID executionId, ExecutionEventType type, String nodeId, String detail) {
        return new ExecutionEvent(UUID.randomUUID(), executionId, type, nodeId, Instant.now(), detail, null);
    }
}
