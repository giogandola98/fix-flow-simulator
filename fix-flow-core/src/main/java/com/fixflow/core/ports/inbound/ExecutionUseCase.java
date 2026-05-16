package com.fixflow.core.ports.inbound;

import com.fixflow.core.domain.execution.ExecutionEvent;
import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.execution.FIXMessage;

import java.util.List;
import java.util.UUID;

public interface ExecutionUseCase {
    UUID start(UUID scenarioId, UUID sessionId);
    void stop(UUID executionId);
    ExecutionStatus getStatus(UUID executionId);
    List<ExecutionEvent> getEvents(UUID executionId);
    List<FIXMessage> getMessages(UUID executionId);
}
