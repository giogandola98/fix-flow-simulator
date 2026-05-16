package com.fixflow.adapters.persistence.entity;

import com.fixflow.core.domain.execution.ExecutionStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "executions")
public class ExecutionEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID scenarioId;

    private String scenarioVersion;
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status;

    private Instant startTime;
    private Instant endTime;
    private String currentNodeId;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String variablesJson;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getScenarioId() { return scenarioId; }
    public void setScenarioId(UUID s) { this.scenarioId = s; }
    public String getScenarioVersion() { return scenarioVersion; }
    public void setScenarioVersion(String v) { this.scenarioVersion = v; }
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID s) { this.sessionId = s; }
    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus s) { this.status = s; }
    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant t) { this.startTime = t; }
    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant t) { this.endTime = t; }
    public String getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(String n) { this.currentNodeId = n; }
    public String getVariablesJson() { return variablesJson; }
    public void setVariablesJson(String v) { this.variablesJson = v; }
}
