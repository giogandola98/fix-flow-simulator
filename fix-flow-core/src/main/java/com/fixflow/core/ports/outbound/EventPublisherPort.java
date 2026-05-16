package com.fixflow.core.ports.outbound;

import com.fixflow.core.domain.execution.ExecutionEvent;

public interface EventPublisherPort {
    void publish(ExecutionEvent event);
}
