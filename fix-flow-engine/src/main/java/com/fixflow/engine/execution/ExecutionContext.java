package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.Scenario;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ExecutionContext {

    private final UUID executionId;
    private final Scenario scenario;
    private final UUID sessionId;
    private final Instant startTime = Instant.now();
    private volatile ExecutionStatus status = ExecutionStatus.RUNNING;
    private volatile String currentNodeId;
    private final Map<String, String> variables = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, String>> nodeMessages = new ConcurrentHashMap<>();

    public ExecutionContext(UUID executionId, Scenario scenario, UUID sessionId) {
        this.executionId = executionId;
        this.scenario = scenario;
        this.sessionId = sessionId;
    }

    public UUID executionId() { return executionId; }
    public Scenario scenario() { return scenario; }
    public UUID sessionId() { return sessionId; }
    public Instant startTime() { return startTime; }
    public ExecutionStatus status() { return status; }
    public void setStatus(ExecutionStatus s) { this.status = s; }
    public String currentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(String id) { this.currentNodeId = id; }
    public Map<String, String> variables() { return variables; }
    public void setVariable(String k, String v) { variables.put(k, v); }
    public String getVariable(String k) { return variables.get(k); }

    public void storeNodeMessage(String nodeId, Map<Integer, String> fields) {
        nodeMessages.put(nodeId, Map.copyOf(fields));
    }

    public Map<Integer, String> getNodeMessage(String nodeId) {
        return nodeMessages.get(nodeId);
    }
}
