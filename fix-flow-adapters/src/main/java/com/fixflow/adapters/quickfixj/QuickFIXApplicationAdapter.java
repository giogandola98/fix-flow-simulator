package com.fixflow.adapters.quickfixj;

import com.fixflow.core.ports.outbound.EventPublisherPort;
import com.fixflow.core.ports.outbound.InboundMessageListener;
import quickfix.*;
import quickfix.Message;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class QuickFIXApplicationAdapter implements Application {

    private volatile InboundMessageListener listener;
    private final EventPublisherPort publisher;
    private final Map<SessionID, UUID> sessionUUIDs = new ConcurrentHashMap<>();

    public QuickFIXApplicationAdapter(InboundMessageListener listener, EventPublisherPort publisher) {
        this.listener = listener;
        this.publisher = publisher;
    }

    public void setInboundListener(InboundMessageListener listener) {
        this.listener = listener;
    }

    public void registerSession(SessionID sid, UUID uuid) {
        sessionUUIDs.put(sid, uuid);
    }

    public void unregisterSession(SessionID sid) {
        sessionUUIDs.remove(sid);
    }

    @Override
    public void onCreate(SessionID sessionId) { /* no-op */ }

    @Override
    public void onLogon(SessionID sessionId) {
        UUID uuid = sessionUUIDs.get(sessionId);
        if (uuid != null) publisher.publishSessionStatus(uuid, "UP");
    }

    @Override
    public void onLogout(SessionID sessionId) {
        UUID uuid = sessionUUIDs.get(sessionId);
        if (uuid != null) publisher.publishSessionStatus(uuid, "DOWN");
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
        UUID uuid = sessionUUIDs.get(sessionId);
        String sessionKey = uuid != null ? uuid.toString() : sessionId.toString();
        listener.onMessage(sessionKey, fields);
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
