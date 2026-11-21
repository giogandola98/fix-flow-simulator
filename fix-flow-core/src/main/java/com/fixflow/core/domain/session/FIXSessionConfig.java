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
        boolean resetOnLogon,
        boolean resetOnLogout,
        String appDataDictionary,
        String transportDataDictionary,
        boolean autoConnect,
        String packId
) {
    /** Legacy shape: built-in dictionaries, no auto-connect, no pack. */
    public FIXSessionConfig(UUID id, String name, FIXMode mode, FIXVersion fixVersion, String defaultApplVerID,
                            String senderCompID, String targetCompID, String host, int port,
                            int heartbeatInterval, boolean resetOnLogon, boolean resetOnLogout) {
        this(id, name, mode, fixVersion, defaultApplVerID, senderCompID, targetCompID, host, port,
                heartbeatInterval, resetOnLogon, resetOnLogout, null, null, false, null);
    }


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
