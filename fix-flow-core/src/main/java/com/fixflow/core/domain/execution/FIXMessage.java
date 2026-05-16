package com.fixflow.core.domain.execution;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record FIXMessage(
        UUID id,
        UUID executionId,
        Direction direction,
        String rawFix,
        Map<Integer, String> fields,
        Instant receivedAt
) {
    public FIXMessage {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }
}
