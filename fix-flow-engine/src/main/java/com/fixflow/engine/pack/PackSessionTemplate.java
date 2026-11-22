package com.fixflow.engine.pack;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fixflow.core.domain.session.FIXMode;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.domain.session.FIXVersion;

import java.util.UUID;

/**
 * A {@code sessions/*.yaml} file: the body of {@code POST /api/v1/sessions} written as YAML. Its
 * {@code appDataDictionary}/{@code transportDataDictionary} name a file of the pack's
 * {@code dictionaries/} folder (or a dictionary already in the engine).
 */
@JsonPropertyOrder({"name", "mode", "fixVersion", "defaultApplVerID", "senderCompID", "targetCompID", "host",
        "port", "heartbeatInterval", "resetOnLogon", "resetOnLogout", "appDataDictionary",
        "transportDataDictionary", "autoConnect"})
public record PackSessionTemplate(String name, String mode, String fixVersion, String defaultApplVerID,
                                  String senderCompID, String targetCompID, String host, Integer port,
                                  Integer heartbeatInterval, Boolean resetOnLogon, Boolean resetOnLogout,
                                  String appDataDictionary, String transportDataDictionary,
                                  Boolean autoConnect) {

    FIXSessionConfig toConfig(UUID id, String packId, String appDictionary, String transportDictionary) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (port == null) throw new IllegalArgumentException("port is required");
        return new FIXSessionConfig(id, name.trim(), parse(FIXMode.class, "mode", mode),
                parse(FIXVersion.class, "fixVersion", fixVersion), blankToNull(defaultApplVerID),
                senderCompID, targetCompID, blankToNull(host), port,
                heartbeatInterval == null ? 30 : heartbeatInterval,
                Boolean.TRUE.equals(resetOnLogon), Boolean.TRUE.equals(resetOnLogout),
                appDictionary, transportDictionary, Boolean.TRUE.equals(autoConnect), packId);
    }

    static PackSessionTemplate fromConfig(FIXSessionConfig c, String appDictionary, String transportDictionary) {
        return new PackSessionTemplate(c.name(), c.mode().name(), c.fixVersion().name(), c.defaultApplVerID(),
                c.senderCompID(), c.targetCompID(), c.host(), c.port(), c.heartbeatInterval(),
                c.resetOnLogon(), c.resetOnLogout(), appDictionary, transportDictionary, c.autoConnect());
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String field, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid " + field + " '" + value + "'");
        }
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
