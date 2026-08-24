package com.fixflow.engine.fix;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.ports.outbound.InboundMessageListener;
import com.fixflow.engine.correlation.CorrelationEngine;
import org.springframework.stereotype.Service;

import java.util.Map;
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
        // TODO(Task 6): carry the full FIXMessageData into the buffer/correlation engine
        // instead of projecting to flat fields here.
        Map<Integer, String> fields = message.flatFields();
        if (buffer.isPaused()) {
            buffer.park(sessionId, fields);
            return;
        }
        boolean consumed = correlation.onMessage(sessionId, fields);
        if (!consumed) buffer.park(sessionId, fields);
    }

    public void drain(String sessionId) {
        Optional<Map<Integer, String>> next;
        do {
            next = buffer.poll(sessionId, fields -> correlation.onMessage(sessionId, fields));
        } while (next.isPresent());
    }
}
