package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.execution.ScenarioExecutorPort;
import com.fixflow.engine.variable.VariableResolver;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs a sub-scenario for CALL_SCENARIO and PARALLEL. A child gets its own execution id (so its
 * correlation waiters never collide with the parent's or a sibling's), the parent's session, the
 * given data set row, the parent's data set, and only the variables named in {@code inputVars}.
 * It emits no events and is not registered as a session execution: the parent node reports for it.
 *
 * <p>Nesting is limited to {@value #MAX_DEPTH} levels through the {@code call:depth} variable.
 * Stateless and thread-safe: PARALLEL calls {@link #run} from several threads at once.
 */
public final class ChildRunner {

    public static final int MAX_DEPTH = 5;
    static final String DEPTH_KEY = "call:depth";

    /** The parent is already {@value #MAX_DEPTH} levels deep. */
    public static final class MaxDepthExceededException extends RuntimeException {
        MaxDepthExceededException() { super("Max call depth exceeded (" + MAX_DEPTH + ")"); }
    }

    private final ScenarioExecutorPort executor;
    private final VariableResolver resolver;

    public ChildRunner(ScenarioExecutorPort executor, VariableResolver resolver) {
        this.executor = executor;
        this.resolver = resolver;
    }

    /** Fails fast when a child of {@code parent} would exceed the depth limit. */
    public static void checkDepth(ExecutionContext parent) {
        if (parseDepth(parent.getVariable(DEPTH_KEY)) + 1 > MAX_DEPTH) throw new MaxDepthExceededException();
    }

    /** Runs {@code target} bound to {@code row}, keeping the parent's row index. */
    public ExecutionContext run(Scenario target, ExecutionContext parent, Map<String, String> row,
                                List<Map<String, String>> inputVars) throws InterruptedException {
        return run(target, parent, row, parent.rowIndex(), inputVars);
    }

    /**
     * Runs {@code target} to completion on the calling thread and returns the child context, whose
     * {@code status()} is the child's outcome. An interrupt propagates as InterruptedException;
     * the child's waiting node has then already cancelled its own correlation waiter.
     */
    public ExecutionContext run(Scenario target, ExecutionContext parent, Map<String, String> row, int rowIndex,
                                List<Map<String, String>> inputVars) throws InterruptedException {
        checkDepth(parent);
        int childDepth = parseDepth(parent.getVariable(DEPTH_KEY)) + 1;

        ExecutionContext child = new ExecutionContext(UUID.randomUUID(), target, parent.sessionId());
        child.setRow(row);
        child.setRowIndex(rowIndex);
        child.setDataSet(parent.dataSet());
        child.setVariable(DEPTH_KEY, String.valueOf(childDepth));

        for (Map<String, String> entry : inputVars == null ? List.<Map<String, String>>of() : inputVars) {
            String from = entry.get("from");
            String to = entry.get("to");
            if (from == null || to == null) continue;
            child.setVariable(to, resolver.resolveAll("{{" + from + "}}", parent));
        }

        executor.execute(target, child);
        return child;
    }

    private static int parseDepth(String value) {
        if (value == null) return 0;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { return 0; }
    }
}
