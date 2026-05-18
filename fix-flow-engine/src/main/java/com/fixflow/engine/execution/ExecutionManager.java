package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.Execution;
import com.fixflow.core.domain.execution.ExecutionEvent;
import com.fixflow.core.domain.execution.ExecutionEventType;
import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.execution.Direction;
import com.fixflow.core.domain.execution.FIXMessage;
import com.fixflow.core.domain.execution.NodeResult;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.core.ports.outbound.EventPublisherPort;
import com.fixflow.core.ports.outbound.ExecutionRepositoryPort;
import com.fixflow.engine.handlers.NodeDispatcher;
import com.fixflow.engine.handlers.NodeHandlerResult;
import com.fixflow.engine.scenario.ScenarioRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ExecutionManager {

    private final ScenarioRegistry registry;
    private final NodeDispatcher dispatcher;
    private final ExecutionRepositoryPort executionRepo;
    private final EventPublisherPort eventPublisher;
    private final Map<UUID, ExecutionContext> contexts = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    public ExecutionManager(ScenarioRegistry registry, NodeDispatcher dispatcher,
                            ExecutionRepositoryPort executionRepo,
                            EventPublisherPort eventPublisher) {
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.executionRepo = executionRepo;
        this.eventPublisher = eventPublisher;
    }

    /** Convenience constructor for unit tests that don't need persistence. */
    public ExecutionManager(ScenarioRegistry registry, NodeDispatcher dispatcher) {
        this(registry, dispatcher, null, null);
    }

    public UUID start(UUID scenarioId, UUID sessionId) {
        Scenario scenario = registry.getById(scenarioId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown scenario: " + scenarioId));
        UUID executionId = UUID.randomUUID();
        ExecutionContext ctx = new ExecutionContext(executionId, scenario, sessionId);
        contexts.put(executionId, ctx);
        if (executionRepo != null) {
            executionRepo.save(new Execution(executionId, scenarioId,
                    scenario.version() == null ? "1" : scenario.version(),
                    sessionId, ExecutionStatus.RUNNING, Instant.now(), null, null,
                    Map.of(), List.of(), List.of()));
        }

        executor.submit(() -> runScenario(ctx));
        return executionId;
    }

    public void stop(UUID executionId) {
        ExecutionContext ctx = contexts.get(executionId);
        if (ctx != null) ctx.setStatus(ExecutionStatus.STOPPED);
    }

    public ExecutionStatus getStatus(UUID executionId) {
        ExecutionContext ctx = contexts.get(executionId);
        return ctx == null ? null : ctx.status();
    }

    public ExecutionContext getContext(UUID executionId) {
        return contexts.get(executionId);
    }

    private void runScenario(ExecutionContext ctx) {
        emitAndPersist(ctx.executionId(), ExecutionEventType.EXECUTION_STARTED, null,
                "Execution started for scenario " + ctx.scenario().id());
        try {
            ScenarioNode current = ctx.scenario().startNode()
                    .orElseThrow(() -> new IllegalStateException("Scenario has no START node"));

            while (current != null && ctx.status() == ExecutionStatus.RUNNING) {
                ctx.setCurrentNodeId(current.id());
                emitAndPersist(ctx.executionId(), ExecutionEventType.NODE_ENTERED, current.id(),
                        "Entering node " + current.name() + " [" + current.type() + "]");

                Instant nodeStart = Instant.now();
                NodeHandlerResult result = dispatcher.dispatch(current, ctx);
                Instant nodeEnd = Instant.now();

                persistNodeResult(ctx.executionId(), current.id(), result, nodeStart, nodeEnd);
                if (current.type() == NodeType.SEND_FIX) {
                    persistMessage(ctx, current.id(), Direction.OUTBOUND);
                } else if (current.type() == NodeType.EXPECT_FIX && result.success()) {
                    persistMessage(ctx, current.id(), Direction.INBOUND);
                }

                if (result.success()) {
                    emitAndPersist(ctx.executionId(), ExecutionEventType.NODE_EXITED, current.id(),
                            "Node " + current.name() + " completed");
                } else {
                    emitAndPersist(ctx.executionId(), ExecutionEventType.ERROR, current.id(),
                            result.errorMessage() != null ? result.errorMessage() : "Node failed");
                }

                if (ctx.status() != ExecutionStatus.RUNNING) break;

                if (!result.success()) {
                    ctx.setStatus(ExecutionStatus.FAILED);
                    break;
                }
                if (result.nextNodeId() == null) break;
                current = ctx.scenario().findNode(result.nextNodeId()).orElse(null);
            }

            if (ctx.status() == ExecutionStatus.RUNNING) {
                ctx.setStatus(ExecutionStatus.PASSED);
            }
        } catch (Throwable t) {
            emitAndPersist(ctx.executionId(), ExecutionEventType.ERROR, ctx.currentNodeId(),
                    t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
            ctx.setStatus(ExecutionStatus.FAILED);
        } finally {
            emitAndPersist(ctx.executionId(), ExecutionEventType.EXECUTION_FINISHED, null,
                    "Execution finished with status " + ctx.status());
            persistFinalStatus(ctx);
        }
    }

    private void emitAndPersist(UUID executionId, ExecutionEventType type, String nodeId, String detail) {
        ExecutionEvent event = ExecutionEvent.of(executionId, type, nodeId, detail);
        if (eventPublisher != null) {
            try { eventPublisher.publish(event); } catch (Throwable ignored) {}
        }
        if (executionRepo != null) {
            try { executionRepo.addEvent(executionId, event); } catch (Throwable ignored) {}
        }
    }

    private void persistMessage(ExecutionContext ctx, String nodeId, Direction direction) {
        Map<Integer, String> fields = ctx.getNodeMessage(nodeId);
        if (fields == null || fields.isEmpty()) return;
        try {
            String raw = fields.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(java.util.stream.Collectors.joining("|"));
            FIXMessage msg = new FIXMessage(
                    UUID.randomUUID(), ctx.executionId(), direction, raw,
                    fields, Instant.now());
            if (executionRepo != null) {
                try { executionRepo.addMessage(ctx.executionId(), msg); } catch (Throwable ignored) {}
            }
            if (eventPublisher != null) {
                try { eventPublisher.publishMessage(ctx.executionId(), msg); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private void persistNodeResult(UUID executionId, String nodeId, NodeHandlerResult result,
                                   Instant start, Instant end) {
        if (executionRepo == null) return;
        try {
            executionRepo.addNodeResult(executionId, new NodeResult(
                    UUID.randomUUID(), executionId, nodeId,
                    result.success() ? "PASSED" : "FAILED",
                    start, end,
                    result.success() ? null : result.errorMessage()));
        } catch (Throwable ignored) {}
    }

    private void persistFinalStatus(ExecutionContext ctx) {
        if (executionRepo == null) return;
        try {
            Scenario s = ctx.scenario();
            executionRepo.save(new Execution(
                    ctx.executionId(), s.id(),
                    s.version() == null ? "1" : s.version(),
                    ctx.sessionId(), ctx.status(),
                    Instant.now(), Instant.now(), ctx.currentNodeId(),
                    ctx.variables(), List.of(), List.of()));
        } catch (Throwable ignored) {
        }
    }
}
