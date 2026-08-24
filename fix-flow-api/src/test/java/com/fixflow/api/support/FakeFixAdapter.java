package com.fixflow.api.support;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.ports.outbound.FIXSessionPort;
import com.fixflow.core.ports.outbound.InboundMessageListener;

import java.util.*;
import java.util.concurrent.*;

/**
 * In-memory {@link FIXSessionPort} used by the full-wiring integration test so no
 * real sockets/QuickFIX connectors are opened. Records sent messages and lets the
 * test inject inbound messages to satisfy correlation waiters.
 */
public class FakeFixAdapter implements FIXSessionPort {

    private final Map<UUID, Boolean> connected = new ConcurrentHashMap<>();
    private final List<Map<Integer, String>> sent = new CopyOnWriteArrayList<>();
    private volatile InboundMessageListener listener;

    @Override
    public void connect(FIXSessionConfig cfg) {
        connected.put(cfg.id(), true);
    }

    @Override
    public void disconnect(UUID id) {
        connected.put(id, false);
    }

    @Override
    public boolean isConnected(UUID id) {
        return connected.getOrDefault(id, false);
    }

    @Override
    public void sendMessage(UUID id, FIXMessageData message) {
        sent.add(new HashMap<>(message.flatFields()));
    }

    @Override
    public void setInboundListener(InboundMessageListener l) {
        this.listener = l;
    }

    public void injectInbound(UUID sessionId, Map<Integer, String> fields) {
        InboundMessageListener l = listener;
        if (l != null) l.onMessage(sessionId.toString(), fields);
    }

    public List<Map<Integer, String>> getSent() {
        return Collections.unmodifiableList(sent);
    }
}
