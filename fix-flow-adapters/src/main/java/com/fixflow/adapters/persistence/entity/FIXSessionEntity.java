package com.fixflow.adapters.persistence.entity;

import com.fixflow.core.domain.session.FIXMode;
import com.fixflow.core.domain.session.FIXVersion;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "fix_sessions")
public class FIXSessionEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FIXMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FIXVersion fixVersion;

    private String defaultApplVerID;
    private String senderCompID;
    private String targetCompID;
    private String host;
    private int port;
    private int heartbeatInterval;
    private int reconnectInterval;
    private boolean resetOnLogon;
    private boolean resetOnLogout;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public FIXMode getMode() { return mode; }
    public void setMode(FIXMode mode) { this.mode = mode; }
    public FIXVersion getFixVersion() { return fixVersion; }
    public void setFixVersion(FIXVersion fixVersion) { this.fixVersion = fixVersion; }
    public String getDefaultApplVerID() { return defaultApplVerID; }
    public void setDefaultApplVerID(String defaultApplVerID) { this.defaultApplVerID = defaultApplVerID; }
    public String getSenderCompID() { return senderCompID; }
    public void setSenderCompID(String s) { this.senderCompID = s; }
    public String getTargetCompID() { return targetCompID; }
    public void setTargetCompID(String t) { this.targetCompID = t; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public int getHeartbeatInterval() { return heartbeatInterval; }
    public void setHeartbeatInterval(int h) { this.heartbeatInterval = h; }
    public int getReconnectInterval() { return reconnectInterval; }
    public void setReconnectInterval(int r) { this.reconnectInterval = r; }
    public boolean isResetOnLogon() { return resetOnLogon; }
    public void setResetOnLogon(boolean r) { this.resetOnLogon = r; }
    public boolean isResetOnLogout() { return resetOnLogout; }
    public void setResetOnLogout(boolean r) { this.resetOnLogout = r; }
}
