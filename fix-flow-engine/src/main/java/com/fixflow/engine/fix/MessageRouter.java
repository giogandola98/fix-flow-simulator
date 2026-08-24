package com.fixflow.engine.fix;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.ports.outbound.InboundMessageListener;
import com.fixflow.engine.correlation.CorrelationEngine;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MessageRouter implements InboundMessageListener {

    private final CorrelationEngine correlation;
    private final MessageBuffer buffer;

    public MessageRouter(CorrelationEngine correlation, MessageBuffer buffer) {
        this.correlation = correlation;
        this.buffer = buffer;
    }

    @Override
    public void onMessage(String sessionId, FIXMessageData message) {
        if (buffer.isPaused()) {
            buffer.park(sessionId, message);
            return;
        }
        boolean consumed = correlation.onMessage(sessionId, message);
        if (!consumed) buffer.park(sessionId, message);
    }

    public void drain(String sessionId) {
        Optional<FIXMessageData> next;
        do {
            next = buffer.poll(sessionId, message -> correlation.onMessage(sessionId, message));
        } while (next.isPresent());
    }
}
