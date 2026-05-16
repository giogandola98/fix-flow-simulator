package com.fixflow.adapters.persistence.entity;

import com.fixflow.core.domain.execution.ExecutionEventType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "execution_events", indexes = @Index(columnList = "executionId"))
public class ExecutionEventEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID executionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionEventType type;

    private String nodeId;

    @Column(nullable = false)
    private Instant timestamp;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String detail;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String rawFix;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getExecutionId() { return executionId; }
    public void setExecutionId(UUID e) { this.executionId = e; }
    public ExecutionEventType getType() { return type; }
    public void setType(ExecutionEventType t) { this.type = t; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String n) { this.nodeId = n; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant t) { this.timestamp = t; }
    public String getDetail() { return detail; }
    public void setDetail(String d) { this.detail = d; }
    public String getRawFix() { return rawFix; }
    public void setRawFix(String r) { this.rawFix = r; }
}
