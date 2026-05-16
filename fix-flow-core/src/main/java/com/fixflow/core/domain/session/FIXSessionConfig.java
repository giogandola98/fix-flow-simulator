package com.fixflow.core.domain.session;

import java.util.UUID;

public record FIXSessionConfig(
        UUID id,
        String name,
        FIXMode mode,
        FIXVersion fixVersion,
        String defaultApplVerID,
        String senderCompID,
        String targetCompID,
        String host,
        int port,
        int heartbeatInterval,
        int reconnectInterval,
        boolean resetOnLogon,
        boolean resetOnLogout
) {
    public FIXSessionConfig {
        if (id == null) throw new IllegalArgumentException("session id required");
        if (fixVersion == null) throw new IllegalArgumentException("fixVersion required");
        if (mode == null) throw new IllegalArgumentException("mode required");
        if (senderCompID == null || senderCompID.isBlank()) throw new IllegalArgumentException("senderCompID required");
        if (targetCompID == null || targetCompID.isBlank()) throw new IllegalArgumentException("targetCompID required");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("port must be 1..65535");
        if (heartbeatInterval < 1) throw new IllegalArgumentException("heartbeatInterval must be >= 1");
    }
}
