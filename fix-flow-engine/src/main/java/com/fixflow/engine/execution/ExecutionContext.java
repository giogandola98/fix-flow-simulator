package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.Scenario;

import com.fixflow.core.domain.execution.ExecutionEventType;
import com.fixflow.core.domain.execution.FIXMessageData;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class ExecutionContext {

    private final UUID executionId;
    private final Scenario scenario;
    private final UUID sessionId;
    private final Instant startTime = Instant.now();
    private volatile ExecutionStatus status = ExecutionStatus.RUNNING;
    private BiConsumer<ExecutionEventType, String> nodeEventEmitter = (type, nodeId) -> {};
    private volatile StepListener stepListener = StepListener.NOOP;
    private volatile String currentNodeId;
    private final Map<String, String> variables = new ConcurrentHashMap<>();
    private final Map<String, FIXMessageData> nodeMessages = new ConcurrentHashMap<>();
    /** The most recent INBOUND message of this run, whichever node received it. */
    private volatile FIXMessageData lastInboundMessage;

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
    public void setNodeEventEmitter(BiConsumer<ExecutionEventType, String> emitter) { this.nodeEventEmitter = emitter; }
    public void emitNodeEvent(ExecutionEventType type, String nodeId) { nodeEventEmitter.accept(type, nodeId); }

    public StepListener stepListener() { return stepListener; }
    public void setStepListener(StepListener listener) { this.stepListener = listener == null ? StepListener.NOOP : listener; }

    public Map<String, String> variables() { return variables; }
    public void setVariable(String k, String v) { variables.put(k, v); }
    public String getVariable(String k) { return variables.get(k); }

    public void storeNodeMessage(String nodeId, FIXMessageData message) {
        nodeMessages.put(nodeId, message);
    }

    public void storeNodeMessage(String nodeId, Map<Integer, String> fields) {
        nodeMessages.put(nodeId, FIXMessageData.ofFields(fields));
    }

    /**
     * Stores a message that arrived from the wire. On top of {@link #storeNodeMessage} it records
     * the message as the run's most recent inbound one, so a VALIDATE node that does not name a
     * {@code sourceNodeId} still has something to validate — the graphical editor has no field
     * for it, so from the GUI it is always absent (issue #77).
     */
    public void storeInboundMessage(String nodeId, FIXMessageData message) {
        storeNodeMessage(nodeId, message);
        this.lastInboundMessage = message;
    }

    /** Flat-map form, for callers and tests that have no repeating groups. */
    public void storeInboundMessage(String nodeId, Map<Integer, String> fields) {
        storeInboundMessage(nodeId, FIXMessageData.ofFields(fields));
    }

    /** The most recent message received from the wire in this run, or null if none yet. */
    public FIXMessageData lastInboundMessage() { return lastInboundMessage; }

    /** Top-level fields of the message stored for {@code nodeId}, or null if none. */
    public Map<Integer, String> getNodeMessage(String nodeId) {
        FIXMessageData m = nodeMessages.get(nodeId);
        return m == null ? null : m.flatFields();
    }

    /** Full message including repeating groups, or null if none. */
    public FIXMessageData getNodeMessageData(String nodeId) {
        return nodeMessages.get(nodeId);
    }
}
