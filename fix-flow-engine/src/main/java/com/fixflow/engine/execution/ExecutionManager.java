package com.fixflow.engine.execution;

import com.fixflow.core.domain.dataset.DataSet;
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
import com.fixflow.core.ports.outbound.DataSetRepositoryPort;
import com.fixflow.core.ports.outbound.EventPublisherPort;
import com.fixflow.core.ports.outbound.ExecutionRepositoryPort;
import com.fixflow.engine.fix.RawFixRenderer;
import com.fixflow.engine.handlers.NodeDispatcher;
import com.fixflow.engine.handlers.NodeHandlerResult;
import com.fixflow.engine.scenario.ScenarioRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class ExecutionManager {

    private static final Logger log = LoggerFactory.getLogger(ExecutionManager.class);

    private final ScenarioRegistry registry;
    private final NodeWalker walker;
    private final ExecutionRepositoryPort executionRepo;
    private final EventPublisherPort eventPublisher;
    private final SessionExecutionRegistry sessionExecutions;
    /** Null in unit tests that run without data sets. */
    private final DataSetRepositoryPort dataSets;
    /** Guards {@link #workers}: which worker thread is running which execution right now. */
    private final Object runLock = new Object();
    private final Map<UUID, Thread> workers = new ConcurrentHashMap<>();
    private final Map<UUID, ExecutionContext> contexts = new ConcurrentHashMap<>();
    /** Size-capped LRU so completed-status history cannot grow unbounded. */
    private final Map<UUID, ExecutionStatus> completedStatuses = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, ExecutionStatus> eldest) {
                    return size() > 1000;
                }
            });
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ExecutionManager(ScenarioRegistry registry, NodeWalker walker,
                            ExecutionRepositoryPort executionRepo,
                            EventPublisherPort eventPublisher,
                            SessionExecutionRegistry sessionExecutions) {
        this(registry, walker, executionRepo, eventPublisher, sessionExecutions, null);
    }

    @Autowired
    public ExecutionManager(ScenarioRegistry registry, NodeWalker walker,
                            ExecutionRepositoryPort executionRepo,
                            EventPublisherPort eventPublisher,
                            SessionExecutionRegistry sessionExecutions,
                            DataSetRepositoryPort dataSets) {
        this.dataSets = dataSets;
        this.registry = registry;
        this.walker = walker;
        this.executionRepo = executionRepo;
        this.eventPublisher = eventPublisher;
        this.sessionExecutions = sessionExecutions == null ? new SessionExecutionRegistry() : sessionExecutions;
    }

    /** Convenience constructor for unit tests that don't share a session registry with a router. */
    public ExecutionManager(ScenarioRegistry registry, NodeWalker walker,
                            ExecutionRepositoryPort executionRepo,
                            EventPublisherPort eventPublisher) {
        this(registry, walker, executionRepo, eventPublisher, new SessionExecutionRegistry());
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
        return start(scenarioId, sessionId, ExecutionInputs.none()).get(0);
    }

    /**
     * Starts a run, or one run per data set row when {@code inputs.allRows()}. The rows run one
     * after another on a single worker; a failed row does not stop the next. Ids come back in row
     * order. Bad inputs (unknown data set, row out of range) are rejected before anything starts.
     */
    public List<UUID> start(UUID scenarioId, UUID sessionId, ExecutionInputs inputs) {
        Scenario scenario = registry.getById(scenarioId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown scenario: " + scenarioId));
        ExecutionInputs in = inputs == null ? ExecutionInputs.none() : inputs;
        UUID resolvedSession = sessionId != null ? sessionId : parseSessionRef(scenario);

        DataSet dataSet = resolveDataSet(scenario, in);
        List<Integer> rowIndexes = new ArrayList<>();
        List<Map<String, String>> rows = new ArrayList<>();
        if (dataSet == null) {
            rowIndexes.add(-1);
            rows.add(null);
        } else if (in.allRows()) {
            if (dataSet.rows().isEmpty()) {
                throw new IllegalArgumentException("Data set '" + dataSet.name() + "' has no rows");
            }
            for (int i = 0; i < dataSet.rows().size(); i++) {
                rowIndexes.add(i);
                rows.add(dataSet.row(i));
            }
        } else {
            int index = in.rowIndex() == null ? 0 : in.rowIndex();
            try {
                rows.add(dataSet.row(index));
            } catch (IndexOutOfBoundsException e) {
                throw new IllegalArgumentException(e.getMessage() + " (data set '" + dataSet.name() + "')");
            }
            rowIndexes.add(index);
        }

        List<ExecutionContext> batch = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            UUID executionId = UUID.randomUUID();
            ExecutionContext ctx = new ExecutionContext(executionId, scenario, resolvedSession);
            scenario.variables().forEach((name, def) -> {
                if (def != null && def.defaultValue() != null) ctx.setVariable(name, def.defaultValue());
            });
            in.vars().forEach(ctx::setVariable);
            ctx.setDataSet(dataSet);
            if (rows.get(i) != null) {
                ctx.setRow(rows.get(i));
                ctx.setRowIndex(rowIndexes.get(i));
            }
            batch.add(ctx);
        }

        for (int i = 0; i < batch.size(); i++) {
            ExecutionContext ctx = batch.get(i);
            contexts.put(ctx.executionId(), ctx);
            if (executionRepo != null) {
                executionRepo.save(new Execution(ctx.executionId(), scenarioId,
                        scenario.version() == null ? "1" : scenario.version(),
                        resolvedSession, ExecutionStatus.RUNNING, Instant.now(), null, null,
                        Map.of(), List.of(), List.of()));
            }
        }

        if (resolvedSession != null) {
            sessionExecutions.register(resolvedSession.toString(), batch.get(0).executionId());
        }

        executor.submit(() -> runBatch(batch));
        return batch.stream().map(ExecutionContext::executionId).toList();
    }

    /** The data set a run reads: the request's, else the scenario's {@code datasetRef} (id or name). */
    private DataSet resolveDataSet(Scenario scenario, ExecutionInputs in) {
        if (in.datasetId() != null) {
            if (dataSets == null) throw new IllegalArgumentException("Data sets are not available");
            return dataSets.findById(in.datasetId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown data set: " + in.datasetId()));
        }
        String ref = scenario.datasetRef();
        if (ref != null && !ref.isBlank() && dataSets != null) {
            Optional<DataSet> found = Optional.empty();
            try {
                found = dataSets.findById(UUID.fromString(ref.trim()));
            } catch (IllegalArgumentException notAnId) {
                // a name, looked up below
            }
            return found.or(() -> dataSets.findByName(ref.trim()))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Scenario '" + scenario.name() + "' refers to unknown data set '" + ref + "'"));
        }
        if (in.allRows() || in.rowIndex() != null) {
            throw new IllegalArgumentException("A row was requested but the scenario has no data set");
        }
        return null;
    }

    /** Runs the rows of one start request in order; each ends in its own status. */
    private void runBatch(List<ExecutionContext> batch) {
        boolean abort = false;
        for (int i = 0; i < batch.size(); i++) {
            ExecutionContext ctx = batch.get(i);
            // Check and register under the lock: a stop either sees the status before the row starts
            // (skipped) or finds the worker registered (interrupted), never neither.
            boolean skip;
            synchronized (runLock) {
                skip = abort || ctx.status() == ExecutionStatus.STOPPED;
                if (!skip) workers.put(ctx.executionId(), Thread.currentThread());
            }
            if (skip) {
                ctx.setStatus(ExecutionStatus.STOPPED);
                finish(ctx);
                continue;
            }
            if (i > 0 && ctx.sessionId() != null) {
                sessionExecutions.register(ctx.sessionId().toString(), ctx.executionId());
            }
            // Only this row is interruptible while it is registered here, so a stop of another
            // row can never land on the one running now.
            try {
                runScenario(ctx);
            } finally {
                synchronized (runLock) {
                    workers.remove(ctx.executionId());
                }
            }
            if (Thread.currentThread().isInterrupted()) {
                // A stop of this row interrupts the worker: that ends the row, not the rest of the batch.
                if (ctx.status() == ExecutionStatus.STOPPED) Thread.interrupted();
                else abort = true;
            }
        }
    }

    /**
     * Resolves a scenario's {@code sessionRef} to a session id. A scenario exported from the
     * editor can carry a non-UUID placeholder (e.g. {@code sessionRef: default}); that must read
     * as "no session bound", not as an IllegalArgumentException thrown out of start() (issue #77).
     */
    private static UUID parseSessionRef(Scenario scenario) {
        String ref = scenario.sessionRef();
        if (ref == null || ref.isBlank()) return null;
        try {
            return UUID.fromString(ref.trim());
        } catch (IllegalArgumentException e) {
            log.warn("Scenario {} has a sessionRef that is not a session id ('{}'); "
                    + "running without a bound session", scenario.id(), ref);
            return null;
        }
    }

    public void stop(UUID executionId) {
        // A row still waiting its turn is skipped when reached. A row that is running right now has
        // its worker interrupted so a node blocked in a wait/sleep/loop unwinds immediately; the
        // lock keeps the interrupt from landing after the row ended and the worker moved on.
        synchronized (runLock) {
            ExecutionContext ctx = contexts.get(executionId);
            if (ctx == null) return; // already finished
            ctx.setStatus(ExecutionStatus.STOPPED);
            Thread worker = workers.get(executionId);
            if (worker != null) worker.interrupt();
        }
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
            finish(ctx);
        }
    }

    private void finish(ExecutionContext ctx) {
        emitAndPersist(ctx.executionId(), ExecutionEventType.EXECUTION_FINISHED, null,
                "Execution finished with status " + ctx.status());
        persistFinalStatus(ctx);
        completedStatuses.put(ctx.executionId(), ctx.status());
        contexts.remove(ctx.executionId());
        if (ctx.sessionId() != null) {
            sessionExecutions.unregister(ctx.sessionId().toString(), ctx.executionId());
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
            // Only the outbound direction is recorded here. Inbound messages are recorded by
            // MessageRouter as they arrive, matched or not — recording them again on node success
            // would duplicate every matched message in the FIX Messages tab.
            if (node.type() == NodeType.SEND_FIX) {
                persistMessage(ctx, node.id(), Direction.OUTBOUND);
            }

            if (result.success()) {
                String exitDetail = "Node " + node.name() + " completed";
                if (node.type() == NodeType.ROUTE_FIX) {
                    String matchedLabel = ctx.getVariable("node:" + node.id() + ":matchedRuleLabel");
                    if (matchedLabel != null) exitDetail = "Routed via rule: " + matchedLabel;
                } else if (node.type() == NodeType.DECISION) {
                    String matchedLabel = ctx.getVariable("node:" + node.id() + ":matchedBranchLabel");
                    if (matchedLabel != null) exitDetail = "Routed via branch: " + matchedLabel;
                } else if (node.type() == NodeType.PARALLEL) {
                    String passed = ctx.getVariable("parallel:" + node.id() + ":passed");
                    String failed = ctx.getVariable("parallel:" + node.id() + ":failed");
                    if (passed != null && failed != null) {
                        exitDetail = passed + "/" + (Integer.parseInt(passed) + Integer.parseInt(failed))
                                + " children passed";
                    }
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
            String raw = RawFixRenderer.render(data);
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
