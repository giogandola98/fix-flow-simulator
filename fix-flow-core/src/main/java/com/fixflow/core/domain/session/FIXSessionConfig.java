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
    }
}
