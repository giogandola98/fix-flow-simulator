package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.Execution;
import com.fixflow.core.domain.execution.ExecutionEvent;
import com.fixflow.core.domain.execution.ExecutionEventType;
import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.execution.Direction;
import com.fixflow.core.domain.execution.FIXMessage;
import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.execution.NodeResult;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.core.ports.outbound.EventPublisherPort;
import com.fixflow.core.ports.outbound.ExecutionRepositoryPort;
import com.fixflow.engine.handlers.NodeDispatcher;
import com.fixflow.engine.handlers.NodeHandlerResult;
import com.fixflow.engine.scenario.ScenarioRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service
public class ExecutionManager {

    private static final Logger log = LoggerFactory.getLogger(ExecutionManager.class);

    private final ScenarioRegistry registry;
    private final NodeWalker walker;
    private final ExecutionRepositoryPort executionRepo;
    private final EventPublisherPort eventPublisher;
    private final Map<UUID, ExecutionContext> contexts = new ConcurrentHashMap<>();
    /** Tracks the running task per execution so a stop can interrupt a blocked node. */
    private final Map<UUID, Future<?>> runningTasks = new ConcurrentHashMap<>();
    /** Size-capped LRU so completed-status history cannot grow unbounded. */
    private final Map<UUID, ExecutionStatus> completedStatuses = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, ExecutionStatus> eldest) {
                    return size() > 1000;
                }
            });
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    public ExecutionManager(ScenarioRegistry registry, NodeWalker walker,
                            ExecutionRepositoryPort executionRepo,
                            EventPublisherPort eventPublisher) {
        this.registry = registry;
        this.walker = walker;
        this.executionRepo = executionRepo;
        this.eventPublisher = eventPublisher;
    }

    /** Convenience constructor for unit tests that don't need persistence. */
    public ExecutionManager(ScenarioRegistry registry, NodeDispatcher dispatcher) {
        this(registry, new NodeWalker(dispatcher), null, null);
    }

    @PreDestroy
    public void shutdown() {
        if (executor == null) return;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public UUID start(UUID scenarioId, UUID sessionId) {
        Scenario scenario = registry.getById(scenarioId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown scenario: " + scenarioId));
        UUID executionId = UUID.randomUUID();
        UUID resolvedSession = sessionId != null ? sessionId
                : (scenario.sessionRef() != null ? UUID.fromString(scenario.sessionRef()) : null);
        ExecutionContext ctx = new ExecutionContext(executionId, scenario, resolvedSession);
        contexts.put(executionId, ctx);
        if (executionRepo != null) {
            executionRepo.save(new Execution(executionId, scenarioId,
                    scenario.version() == null ? "1" : scenario.version(),
                    resolvedSession, ExecutionStatus.RUNNING, Instant.now(), null, null,
                    Map.of(), List.of(), List.of()));
        }

        Future<?> task = executor.submit(() -> runScenario(ctx));
        runningTasks.put(executionId, task);
        // Guard the rare case where the run finished before we recorded the task.
        if (task.isDone()) runningTasks.remove(executionId);
        return executionId;
    }

    public void stop(UUID executionId) {
        ExecutionContext ctx = contexts.get(executionId);
        if (ctx != null) ctx.setStatus(ExecutionStatus.STOPPED);
        // Interrupt the worker so a node blocked in a wait/sleep/loop unwinds immediately.
        Future<?> task = runningTasks.get(executionId);
        if (task != null) task.cancel(true);
    }

    public ExecutionStatus getStatus(UUID executionId) {
        ExecutionContext ctx = contexts.get(executionId);
        if (ctx != null) return ctx.status();
        return completedStatuses.get(executionId);
    }

    public ExecutionContext getContext(UUID executionId) {
        return contexts.get(executionId);
    }

    private void runScenario(ExecutionContext ctx) {
        // Per-node persistence/eventing lives in the walker's StepListener so that nodes executed
        // inside a LOOP sub-block are recorded exactly like top-level nodes.
        ctx.setStepListener(new PersistingStepListener());
        // Kept for handlers (e.g. RetryHandler) that emit node events directly.
        ctx.setNodeEventEmitter((type, nodeId) -> emitAndPersist(ctx.executionId(), type, nodeId,
                type + " " + nodeId));
        emitAndPersist(ctx.executionId(), ExecutionEventType.EXECUTION_STARTED, null,
                "Execution started for scenario " + ctx.scenario().id());
        try {
            ScenarioNode start = ctx.scenario().startNode()
                    .orElseThrow(() -> new IllegalStateException("Scenario has no START node"));

            WalkOutcome outcome = walker.walk(start, ctx, null);

            if (outcome == WalkOutcome.FAILED) {
                ctx.setStatus(ExecutionStatus.FAILED);
            } else if (ctx.status() == ExecutionStatus.RUNNING) {
                // COMPLETED (ran off the end without an explicit END node).
                ctx.setStatus(ExecutionStatus.PASSED);
            }
            // HALTED: an END node or a stop already set the terminal status — leave it.
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            // A stop request already set STOPPED; only an unexpected interrupt marks it FAILED.
            if (ctx.status() != ExecutionStatus.STOPPED) {
                emitAndPersist(ctx.executionId(), ExecutionEventType.ERROR, ctx.currentNodeId(),
                        "Execution interrupted");
                if (ctx.status() == ExecutionStatus.RUNNING) ctx.setStatus(ExecutionStatus.FAILED);
            }
        } catch (Throwable t) {
            emitAndPersist(ctx.executionId(), ExecutionEventType.ERROR, ctx.currentNodeId(),
                    t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
            if (ctx.status() == ExecutionStatus.RUNNING) ctx.setStatus(ExecutionStatus.FAILED);
        } finally {
            emitAndPersist(ctx.executionId(), ExecutionEventType.EXECUTION_FINISHED, null,
                    "Execution finished with status " + ctx.status());
            persistFinalStatus(ctx);
            completedStatuses.put(ctx.executionId(), ctx.status());
            contexts.remove(ctx.executionId());
            runningTasks.remove(ctx.executionId());
        }
    }

    /** Emits events and persists node results/messages for every node the walker visits. */
    private final class PersistingStepListener implements StepListener {
        @Override
        public void entered(ScenarioNode node, ExecutionContext ctx) {
            emitAndPersist(ctx.executionId(), ExecutionEventType.NODE_ENTERED, node.id(),
                    "Entering node " + node.name() + " [" + node.type() + "]");
        }

        @Override
        public void completed(ScenarioNode node, NodeHandlerResult result,
                              Instant start, Instant end, ExecutionContext ctx) {
            persistNodeResult(ctx.executionId(), node.id(), result, start, end);
            if (node.type() == NodeType.SEND_FIX) {
                persistMessage(ctx, node.id(), Direction.OUTBOUND);
            } else if ((node.type() == NodeType.EXPECT_FIX || node.type() == NodeType.ROUTE_FIX) && result.success()) {
                persistMessage(ctx, node.id(), Direction.INBOUND);
            }

            if (result.success()) {
                String exitDetail = "Node " + node.name() + " completed";
                if (node.type() == NodeType.ROUTE_FIX) {
                    String matchedLabel = ctx.getVariable("node:" + node.id() + ":matchedRuleLabel");
                    if (matchedLabel != null) exitDetail = "Routed via rule: " + matchedLabel;
                }
                emitAndPersist(ctx.executionId(), ExecutionEventType.NODE_EXITED, node.id(), exitDetail);
            } else {
                emitAndPersist(ctx.executionId(), ExecutionEventType.ERROR, node.id(),
                        result.errorMessage() != null ? result.errorMessage() : "Node failed");
            }
        }
    }

    private void emitAndPersist(UUID executionId, ExecutionEventType type, String nodeId, String detail) {
        ExecutionEvent event = ExecutionEvent.of(executionId, type, nodeId, detail);
        if (eventPublisher != null) {
            try { eventPublisher.publish(event); } catch (Throwable t) {
                log.warn("Failed to publish event {} for execution {}: {}", type, executionId, t.getMessage());
            }
        }
        if (executionRepo != null) {
            try { executionRepo.addEvent(executionId, event); } catch (Throwable t) {
                log.warn("Failed to persist event {} for execution {}: {}", type, executionId, t.getMessage());
            }
        }
    }

    private void persistMessage(ExecutionContext ctx, String nodeId, Direction direction) {
        FIXMessageData data = ctx.getNodeMessageData(nodeId);
        if (data == null || (data.fields().isEmpty() && data.groups().isEmpty())) return;
        try {
            String raw = renderRawFix(data);
            FIXMessage msg = new FIXMessage(
                    UUID.randomUUID(), ctx.executionId(), direction, raw,
                    data.fields(), Instant.now());
            if (executionRepo != null) {
                try { executionRepo.addMessage(ctx.executionId(), msg); } catch (Throwable t) {
                    log.warn("Failed to save message for execution {}: {}", ctx.executionId(), t.getMessage());
                }
            }
            if (eventPublisher != null) {
                try { eventPublisher.publishMessage(ctx.executionId(), msg); } catch (Throwable t) {
                    log.warn("Failed to publish message for execution {}: {}", ctx.executionId(), t.getMessage());
                }
            }
        } catch (Throwable t) {
            log.warn("Failed to persist message for execution {}: {}", ctx.executionId(), t.getMessage());
        }
    }

    /**
     * Renders a top-level {@link FIXMessageData} into the pipe-delimited raw form
     * persisted/published for the FIX Messages tab. Top-level fields are sorted by tag for a
     * stable, readable record — safe here because top-level fields carry no delimiter ordering
     * requirement. Group entries are rendered by {@link #renderEntry}, which must NOT sort.
     */
    private static String renderRawFix(FIXMessageData data) {
        List<String> parts = new java.util.ArrayList<>();
        data.fields().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> parts.add(e.getKey() + "=" + e.getValue()));
        data.groups().forEach((counterTag, entries) -> {
            parts.add(counterTag + "=" + entries.size());
            for (FIXMessageData entry : entries) {
                parts.add(renderEntry(entry));
            }
        });
        return String.join("|", parts);
    }

    /**
     * Renders one repeating-group entry (and any nested sub-groups) in original insertion order —
     * deliberately UNSORTED, unlike {@link #renderRawFix}'s top-level fields. The first field of
     * an entry is the FIX delimiter tag, read positionally by {@code QuickFIXAdapter.applyGroups};
     * sorting by tag here would routinely move a lower-numbered field (e.g. LegSettlType 587)
     * ahead of the delimiter (e.g. LegSymbol 600) and misrepresent what was actually sent/received.
     */
    private static String renderEntry(FIXMessageData entry) {
        List<String> parts = new java.util.ArrayList<>();
        entry.fields().forEach((tag, value) -> parts.add(tag + "=" + value));
        entry.groups().forEach((counterTag, nested) -> {
            parts.add(counterTag + "=" + nested.size());
            for (FIXMessageData nestedEntry : nested) {
                parts.add(renderEntry(nestedEntry));
            }
        });
        return String.join("|", parts);
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
        } catch (Throwable t) {
            log.warn("Failed to persist node result for execution {}/{}: {}", executionId, nodeId, t.getMessage());
        }
    }

    private void persistFinalStatus(ExecutionContext ctx) {
        if (executionRepo == null) return;
        try {
            Scenario s = ctx.scenario();
            executionRepo.save(new Execution(
                    ctx.executionId(), s.id(),
                    s.version() == null ? "1" : s.version(),
                    ctx.sessionId(), ctx.status(),
                    ctx.startTime(), Instant.now(), ctx.currentNodeId(),
                    ctx.variables(), List.of(), List.of()));
        } catch (Throwable t) {
            log.error("Failed to persist final status for execution {}: {}", ctx.executionId(), t.getMessage(), t);
        }
    }
}
