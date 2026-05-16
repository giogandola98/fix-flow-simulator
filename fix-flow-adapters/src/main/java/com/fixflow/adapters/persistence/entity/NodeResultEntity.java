package com.fixflow.adapters.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "node_results", indexes = @Index(columnList = "executionId"))
public class NodeResultEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID executionId;

    @Column(nullable = false)
    private String nodeId;

    @Column(nullable = false)
    private String status;

    private Instant startTime;
    private Instant endTime;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String error;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getExecutionId() { return executionId; }
    public void setExecutionId(UUID e) { this.executionId = e; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String n) { this.nodeId = n; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant t) { this.startTime = t; }
    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant t) { this.endTime = t; }
    public String getError() { return error; }
    public void setError(String e) { this.error = e; }
}
