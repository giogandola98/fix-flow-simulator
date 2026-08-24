package com.fixflow.engine.support;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.ports.outbound.FIXSessionPort;
import com.fixflow.core.ports.outbound.InboundMessageListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory {@link FIXSessionPort} test double: records outbound sends, allows inbound injection. */
public class FakeFixAdapter implements FIXSessionPort {

    private final Map<UUID, Boolean> connected = new ConcurrentHashMap<>();
    private final List<Map<Integer, String>> sentMessages = new CopyOnWriteArrayList<>();
    private volatile InboundMessageListener listener;

    @Override public void connect(FIXSessionConfig config) { connected.put(config.id(), true); }
    @Override public void disconnect(UUID sessionId) { connected.put(sessionId, false); }
    @Override public boolean isConnected(UUID sessionId) { return connected.getOrDefault(sessionId, false); }

    @Override public void sendMessage(UUID sessionId, FIXMessageData message) {
        sentMessages.add(new HashMap<>(message.flatFields()));
    }

    @Override public void setInboundListener(InboundMessageListener l) { this.listener = l; }

    public void injectInbound(UUID sessionId, Map<Integer, String> fields) {
        InboundMessageListener l = listener;
        if (l != null) l.onMessage(sessionId.toString(), fields);
    }

    public InboundMessageListener listener() { return listener; }
    public List<Map<Integer, String>> sent() { return List.copyOf(sentMessages); }
    public Map<Integer, String> lastSent() { return sentMessages.isEmpty() ? null : sentMessages.get(sentMessages.size() - 1); }
    public void reset() { sentMessages.clear(); connected.clear(); }
}
