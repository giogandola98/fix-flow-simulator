package com.fixflow.engine.fix;

import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.ports.outbound.FIXSessionPort;
import com.fixflow.core.ports.outbound.InboundMessageListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FIXSessionManager {

    private final FIXSessionPort port;
    private final Map<UUID, FIXSessionConfig> known = new ConcurrentHashMap<>();

    public FIXSessionManager(FIXSessionPort port) { this.port = port; }

    public void registerListener(InboundMessageListener listener) {
        port.setInboundListener(listener);
    }

    public void connect(FIXSessionConfig cfg) {
        known.put(cfg.id(), cfg);
        port.connect(cfg);
    }

    public void disconnect(UUID id) {
        known.remove(id);
        port.disconnect(id);
    }

    public boolean isConnected(UUID id) { return port.isConnected(id); }
}
