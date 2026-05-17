package com.fixflow.core.ports.outbound;

import com.fixflow.core.domain.execution.Execution;
import com.fixflow.core.domain.execution.ExecutionEvent;
import com.fixflow.core.domain.execution.FIXMessage;
import com.fixflow.core.domain.execution.NodeResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecutionRepositoryPort {
    Execution save(Execution execution);
    Optional<Execution> findById(UUID id);
    void addEvent(UUID executionId, ExecutionEvent event);
    void addMessage(UUID executionId, FIXMessage message);
    void addNodeResult(UUID executionId, NodeResult result);
    List<FIXMessage> findMessages(UUID executionId);
}
