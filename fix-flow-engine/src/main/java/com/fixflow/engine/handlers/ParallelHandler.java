package com.fixflow.engine.handlers;

import com.fixflow.core.domain.dataset.DataSet;
import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.core.domain.scenario.TimeoutAction;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.execution.ScenarioExecutorPort;
import com.fixflow.engine.scenario.ScenarioRegistry;
import com.fixflow.engine.variable.VariableResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PARALLEL: runs one child of {@code targetScenarioId} per selected row of the execution's data
 * set, all at the same time on virtual threads and on the parent's session, and waits for all of
 * them. Each child is bound to its own row, so its EXPECT_FIX/ROUTE_FIX can correlate on a value
 * of that row (e.g. an inquiry id) while its siblings wait on the same session for the same
 * MsgType; {@code CorrelationEngine} hands each inbound message to the most specific waiter.
 *
 * <p>The node timeout covers the whole node: when it expires the children still running are
 * interrupted, which makes their waiting node cancel its correlation waiter, and are reported as
 * {@code TIMEOUT}. Without a timeout the node waits as long as its children do.
 */
@Component
public class ParallelHandler implements NodeHandler {

    private static final Logger log = LoggerFactory.getLogger(ParallelHandler.class);

    static final int DEFAULT_MAX_CONCURRENCY = 10;
    /** How long to wait for interrupted children to unwind before the node returns anyway. */
    private static final long UNWIND_MS = 5_000;

    private final ScenarioRegistry registry;
    private final ChildRunner children;

    @Autowired
    public ParallelHandler(ScenarioRegistry registry,
                           @Lazy ScenarioExecutorPort executor,
                           VariableResolver resolver) {
        this.registry = registry;
        this.children = new ChildRunner(executor, resolver);
    }

    @Override
    public NodeType getSupportedType() { return NodeType.PARALLEL; }

    @Override
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) throws InterruptedException {
        Map<String, Object> cfg = node.config();
        String rawId = cfg.get("targetScenarioId") == null ? null : String.valueOf(cfg.get("targetScenarioId"));
        if (rawId == null || rawId.isBlank()) {
            return NodeHandlerResult.failure(node.onFailure(), "No target scenario configured");
        }
        UUID targetId;
        try {
            targetId = UUID.fromString(rawId.trim());
        } catch (IllegalArgumentException e) {
            return NodeHandlerResult.failure(node.onFailure(), "Invalid targetScenarioId: " + rawId);
        }
        Scenario target = registry.getById(targetId).orElse(null);
        if (target == null) {
            return NodeHandlerResult.failure(node.onFailure(), "Scenario not found: " + targetId);
        }

        DataSet dataSet = ctx.dataSet();
        if (dataSet == null) {
            return NodeHandlerResult.failure(node.onFailure(),
                    "PARALLEL needs a data set bound to the execution (inputs.datasetId or the scenario's datasetRef)");
        }
        List<Integer> rows;
        int maxConcurrency;
        try {
            rows = selectRows(cfg.get("rows"), dataSet);
            maxConcurrency = parseMaxConcurrency(cfg.get("maxConcurrency"));
            ChildRunner.checkDepth(ctx);
        } catch (IllegalArgumentException | ChildRunner.MaxDepthExceededException e) {
            return NodeHandlerResult.failure(node.onFailure(), e.getMessage());
        }
        List<Map<String, String>> inputVars = CallScenarioHandler.readVarList(cfg, "inputVars");

        Map<Integer, String> statuses = new ConcurrentHashMap<>();
        AtomicBoolean deadlinePassed = new AtomicBoolean();
        Semaphore permits = new Semaphore(maxConcurrency);
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        for (int index : rows) {
            pool.submit(() -> {
                permits.acquire();
                try {
                    ExecutionContext child = children.run(target, ctx, dataSet.row(index), index, inputVars);
                    if (!deadlinePassed.get()) statuses.put(index, child.status().name());
                } catch (InterruptedException e) {
                    // Interrupted by the deadline or by a stop of the parent: reported below.
                } catch (RuntimeException e) {
                    log.warn("PARALLEL node {}: child for row {} failed: {}", node.id(), index, e.toString());
                    if (!deadlinePassed.get()) statuses.put(index, ExecutionStatus.FAILED.name());
                } finally {
                    permits.release();
                }
                return null;
            });
        }
        pool.shutdown();

        boolean finished;
        try {
            finished = node.timeout() == null
                    ? pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)
                    : pool.awaitTermination(node.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException stop) {
            // The parent is being stopped: take the children down with it.
            pool.shutdownNow();
            awaitUnwind(pool, node);
            throw stop;
        }
        if (!finished) {
            deadlinePassed.set(true);
            pool.shutdownNow();
            awaitUnwind(pool, node);
        }

        int passed = 0;
        for (int index : rows) {
            String status = statuses.getOrDefault(index, finished ? ExecutionStatus.FAILED.name() : "TIMEOUT");
            ctx.setVariable("parallel:" + node.id() + ":" + index + ":status", status);
            if (ExecutionStatus.PASSED.name().equals(status)) passed++;
        }
        ctx.setVariable("parallel:" + node.id() + ":passed", String.valueOf(passed));
        ctx.setVariable("parallel:" + node.id() + ":failed", String.valueOf(rows.size() - passed));
        String summary = passed + "/" + rows.size() + " children passed";

        if (!finished) {
            TimeoutAction action = node.timeout().onTimeout() == null ? TimeoutAction.FAIL : node.timeout().onTimeout();
            return switch (action) {
                case CONTINUE -> NodeHandlerResult.success(node.onSuccess());
                case JUMP -> NodeHandlerResult.success(node.timeout().jumpTo());
                case FAIL, RETRY -> NodeHandlerResult.failure(node.onFailure(), "timeout: " + summary);
            };
        }
        return passed == rows.size()
                ? NodeHandlerResult.success(node.onSuccess())
                : NodeHandlerResult.failure(node.onFailure(), summary);
    }

    private static void awaitUnwind(ExecutorService pool, ScenarioNode node) {
        try {
            if (!pool.awaitTermination(UNWIND_MS, TimeUnit.MILLISECONDS)) {
                log.warn("PARALLEL node {}: some children did not stop within {} ms of being interrupted",
                        node.id(), UNWIND_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** {@code rows}: absent or {@code "all"} selects every row; else a list of 0-based indexes. */
    static List<Integer> selectRows(Object raw, DataSet dataSet) {
        int size = dataSet.rows().size();
        List<Integer> rows = new ArrayList<>();
        if (raw == null || (raw instanceof String s && (s.isBlank() || s.trim().equalsIgnoreCase("all")))) {
            for (int i = 0; i < size; i++) rows.add(i);
        } else if (raw instanceof List<?> list) {
            for (Object item : list) {
                int index;
                try {
                    index = item instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(item).trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid row index in rows: " + item);
                }
                if (index < 0 || index >= size) {
                    throw new IllegalArgumentException("row " + index + " out of range, data set '"
                            + dataSet.name() + "' has " + size + " rows");
                }
                if (!rows.contains(index)) rows.add(index);
            }
        } else {
            throw new IllegalArgumentException("rows must be \"all\" or a list of row indexes, got: " + raw);
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("PARALLEL selected no rows (data set '" + dataSet.name() + "')");
        }
        return rows;
    }

    static int parseMaxConcurrency(Object raw) {
        if (raw == null || (raw instanceof String s && s.isBlank())) return DEFAULT_MAX_CONCURRENCY;
        int value;
        try {
            value = raw instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid maxConcurrency: " + raw);
        }
        if (value < 1) throw new IllegalArgumentException("maxConcurrency must be at least 1, got " + value);
        return value;
    }
}
