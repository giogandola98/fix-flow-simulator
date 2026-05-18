package com.fixflow.api.rest.dto;

import com.fixflow.core.domain.session.FIXSessionConfig;
import java.util.UUID;

public record FIXSessionDto(
    UUID id, String name, String mode, String fixVersion, String defaultApplVerID,
    String senderCompID, String targetCompID, String host, int port,
    int heartbeatInterval, boolean resetOnLogon, boolean resetOnLogout,
    boolean connected
) {
    public static FIXSessionDto from(FIXSessionConfig c, boolean connected) {
        return new FIXSessionDto(
            c.id(), c.name(), c.mode().name(), c.fixVersion().name(), c.defaultApplVerID(),
            c.senderCompID(), c.targetCompID(), c.host(), c.port(),
            c.heartbeatInterval(), c.resetOnLogon(), c.resetOnLogout(),
            connected
        );
    }
}
