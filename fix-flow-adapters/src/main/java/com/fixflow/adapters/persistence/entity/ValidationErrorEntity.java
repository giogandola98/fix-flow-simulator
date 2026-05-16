package com.fixflow.adapters.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "validation_errors", indexes = @Index(columnList = "executionId"))
public class ValidationErrorEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID executionId;

    private String nodeId;
    private String message;
    private Instant occurredAt;

    public UUID getId() { return id; }
    public UUID getExecutionId() { return executionId; }
    public void setExecutionId(UUID e) { this.executionId = e; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String n) { this.nodeId = n; }
    public String getMessage() { return message; }
    public void setMessage(String m) { this.message = m; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant o) { this.occurredAt = o; }
}
