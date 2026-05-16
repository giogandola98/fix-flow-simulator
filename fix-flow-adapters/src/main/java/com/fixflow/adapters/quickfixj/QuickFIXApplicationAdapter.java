package com.fixflow.adapters.quickfixj;

import com.fixflow.core.domain.execution.ExecutionEvent;
import com.fixflow.core.domain.execution.ExecutionEventType;
import com.fixflow.core.ports.outbound.EventPublisherPort;
import com.fixflow.core.ports.outbound.InboundMessageListener;
import quickfix.*;
import quickfix.Message;

import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class QuickFIXApplicationAdapter implements Application {

    private final InboundMessageListener listener;
    private final EventPublisherPort publisher;

    public QuickFIXApplicationAdapter(InboundMessageListener listener, EventPublisherPort publisher) {
        this.listener = listener;
        this.publisher = publisher;
    }

    @Override
    public void onCreate(SessionID sessionId) { /* no-op */ }

    @Override
    public void onLogon(SessionID sessionId) {
        publisher.publish(new ExecutionEvent(
                UUID.randomUUID(), null, ExecutionEventType.SESSION_UP, null,
                Instant.now(), "Session up: " + sessionId, null));
    }

    @Override
    public void onLogout(SessionID sessionId) {
        publisher.publish(new ExecutionEvent(
                UUID.randomUUID(), null, ExecutionEventType.SESSION_DOWN, null,
                Instant.now(), "Session down: " + sessionId, null));
    }

    @Override
    public void toAdmin(Message message, SessionID sessionId) { /* no-op */ }

    @Override
    public void fromAdmin(Message message, SessionID sessionId) { /* no-op */ }

    @Override
    public void toApp(Message message, SessionID sessionId) { /* no-op */ }

    @Override
    public void fromApp(Message message, SessionID sessionId) {
        Map<Integer, String> fields = extractFields(message);
        listener.onMessage(sessionId.toString(), fields);
    }

    private Map<Integer, String> extractFields(Message message) {
        Map<Integer, String> fields = new HashMap<>();
        copyFields(message.getHeader().iterator(), fields);
        copyFields(message.iterator(), fields);
        copyFields(message.getTrailer().iterator(), fields);
        return fields;
    }

    private void copyFields(Iterator<Field<?>> it, Map<Integer, String> out) {
        while (it.hasNext()) {
            Field<?> f = it.next();
            out.put(f.getTag(), String.valueOf(f.getObject()));
        }
    }
}
