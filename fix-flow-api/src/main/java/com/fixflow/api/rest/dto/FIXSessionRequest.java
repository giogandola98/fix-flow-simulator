package com.fixflow.api.rest.dto;

public record FIXSessionRequest(
    String name,
    String mode,           // INITIATOR or ACCEPTOR
    String fixVersion,     // FIX_42, FIX_44, or FIXT_11
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
    Boolean autoConnect    // optional: absent = false on create, unchanged on update
) {
    public FIXSessionRequest(String name, String mode, String fixVersion, String defaultApplVerID,
                             String senderCompID, String targetCompID, String host, int port,
                             int heartbeatInterval, boolean resetOnLogon, boolean resetOnLogout,
                             String appDataDictionary, String transportDataDictionary) {
        this(name, mode, fixVersion, defaultApplVerID, senderCompID, targetCompID, host, port,
            heartbeatInterval, resetOnLogon, resetOnLogout, appDataDictionary, transportDataDictionary, null);
    }
}
