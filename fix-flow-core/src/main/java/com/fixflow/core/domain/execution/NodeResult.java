package com.fixflow.core.domain.execution;

import java.time.Instant;
import java.util.UUID;

public record NodeResult(
        UUID id,
        UUID executionId,
        String nodeId,
        String status,
        Instant startTime,
        Instant endTime,
        String error
) {}
