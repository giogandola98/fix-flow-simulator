package com.fixflow.adapters.persistence.entity;

import com.fixflow.core.domain.execution.Direction;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fix_messages", indexes = @Index(columnList = "executionId"))
public class FIXMessageEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID executionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String rawFix;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String fieldsJson;

    @Column(nullable = false)
    private Instant receivedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getExecutionId() { return executionId; }
    public void setExecutionId(UUID e) { this.executionId = e; }
    public Direction getDirection() { return direction; }
    public void setDirection(Direction d) { this.direction = d; }
    public String getRawFix() { return rawFix; }
    public void setRawFix(String r) { this.rawFix = r; }
    public String getFieldsJson() { return fieldsJson; }
    public void setFieldsJson(String f) { this.fieldsJson = f; }
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant r) { this.receivedAt = r; }
}
