package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.Execution;
import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.ScenarioNode;
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
    private final Map<UUID, ExecutionContext> contexts = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    public ExecutionManager(ScenarioRegistry registry, NodeDispatcher dispatcher,
                            ExecutionRepositoryPort executionRepo) {
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.executionRepo = executionRepo;
    }

    /** Convenience constructor for unit tests that don't need persistence. */
    public ExecutionManager(ScenarioRegistry registry, NodeDispatcher dispatcher) {
        this(registry, dispatcher, null);
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
        try {
            ScenarioNode current = ctx.scenario().startNode()
                    .orElseThrow(() -> new IllegalStateException("Scenario has no START node"));

            while (current != null && ctx.status() == ExecutionStatus.RUNNING) {
                ctx.setCurrentNodeId(current.id());
                NodeHandlerResult result = dispatcher.dispatch(current, ctx);

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
            ctx.setStatus(ExecutionStatus.FAILED);
        } finally {
            persistFinalStatus(ctx);
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
                    Instant.now(), Instant.now(), ctx.currentNodeId(),
                    ctx.variables(), List.of(), List.of()));
        } catch (Throwable ignored) {
        }
    }
}
