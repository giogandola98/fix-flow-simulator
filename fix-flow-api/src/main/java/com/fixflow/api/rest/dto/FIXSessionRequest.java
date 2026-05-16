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
    int reconnectInterval,
    boolean resetOnLogon,
    boolean resetOnLogout
) {}
