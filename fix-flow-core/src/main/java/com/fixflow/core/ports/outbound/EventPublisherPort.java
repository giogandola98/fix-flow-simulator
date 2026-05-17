package com.fixflow.core.ports.outbound;

import com.fixflow.core.domain.execution.ExecutionEvent;
import java.util.UUID;

public interface EventPublisherPort {
    void publish(ExecutionEvent event);
    default void publishSessionStatus(UUID sessionId, String status) { /* no-op by default */ }
}
