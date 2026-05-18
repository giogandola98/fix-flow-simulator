package com.fixflow.core.ports.outbound;

import com.fixflow.core.domain.execution.ExecutionEvent;
import com.fixflow.core.domain.execution.FIXMessage;
import java.util.UUID;

public interface EventPublisherPort {
    void publish(ExecutionEvent event);
    default void publishMessage(UUID executionId, FIXMessage message) { /* no-op by default */ }
    default void publishSessionStatus(UUID sessionId, String status) { /* no-op by default */ }
}
