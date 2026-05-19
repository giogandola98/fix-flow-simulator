package com.fixflow.engine.handlers;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.execution.ScenarioExecutor;
import com.fixflow.engine.scenario.ScenarioRegistry;
import com.fixflow.engine.variable.VariableResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CallScenarioHandler implements NodeHandler {

    private static final int MAX_DEPTH = 5;
    private static final String DEPTH_KEY = "call:depth";

    private final ScenarioRegistry registry;
    private final ScenarioExecutor executor;
    private final VariableResolver resolver;

    @Autowired
    public CallScenarioHandler(ScenarioRegistry registry,
                               @Lazy ScenarioExecutor executor,
                               VariableResolver resolver) {
        this.registry = registry;
        this.executor = executor;
        this.resolver = resolver;
    }

    @Override
    public NodeType getSupportedType() { return NodeType.CALL_SCENARIO; }

    @Override
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx)
            throws InterruptedException {

        String rawId = (String) node.config().get("targetScenarioId");
        if (rawId == null || rawId.isBlank()) {
            return NodeHandlerResult.failure(node.onFailure(), "No target scenario configured");
        }

        UUID targetId;
        try {
            targetId = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            return NodeHandlerResult.failure(node.onFailure(), "Invalid targetScenarioId: " + rawId);
        }
        Scenario target = registry.getById(targetId).orElse(null);
        if (target == null) {
            return NodeHandlerResult.failure(node.onFailure(), "Scenario not found: " + targetId);
        }

        int depth = parseDepth(ctx.getVariable(DEPTH_KEY));
        if (depth >= MAX_DEPTH) {
            return NodeHandlerResult.failure(node.onFailure(),
                    "Max call depth exceeded (" + MAX_DEPTH + ")");
        }

        ExecutionContext childCtx = new ExecutionContext(UUID.randomUUID(), target, ctx.sessionId());
        childCtx.setVariable(DEPTH_KEY, String.valueOf(depth + 1));

        List<Map<String, String>> inputVars = readVarList(node.config(), "inputVars");
        for (Map<String, String> entry : inputVars) {
            String from = entry.get("from");
            String to = entry.get("to");
            if (from == null || to == null) continue;
            String resolved = resolver.resolveAll("{{" + from + "}}", ctx);
            childCtx.setVariable(to, resolved);
        }

        ExecutionStatus childStatus = executor.execute(target, childCtx);

        if (childStatus == ExecutionStatus.STOPPED) {
            ctx.setStatus(ExecutionStatus.STOPPED);
            return NodeHandlerResult.failure(node.onFailure(), "Sub-scenario was stopped");
        }

        List<Map<String, String>> outputVars = readVarList(node.config(), "outputVars");
        for (Map<String, String> entry : outputVars) {
            String from = entry.get("from");
            String to = entry.get("to");
            if (from == null || to == null) continue;
            String value = childCtx.getVariable(from);
            if (value == null) continue;
            ctx.setVariable(to, value);
        }

        if (childStatus == ExecutionStatus.PASSED) {
            return NodeHandlerResult.success(node.onSuccess());
        }
        return NodeHandlerResult.failure(node.onFailure(),
                "Sub-scenario ended with FAIL: " + target.name());
    }

    private static int parseDepth(String value) {
        if (value == null) return 0;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { return 0; }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> readVarList(Map<String, Object> config, String key) {
        Object raw = config.get(key);
        if (raw instanceof List<?> list) {
            return (List<Map<String, String>>) list;
        }
        return List.of();
    }
}
