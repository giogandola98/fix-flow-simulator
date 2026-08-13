package com.fixflow.engine.support;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.handlers.NodeHandler;
import com.fixflow.engine.handlers.NodeHandlerResult;

import java.util.function.BiFunction;

/** Test handler bound to a chosen {@link NodeType} with a supplied behaviour. */
public final class ProgrammableHandler implements NodeHandler {

    private final NodeType type;
    private final BiFunction<ScenarioNode, ExecutionContext, NodeHandlerResult> behaviour;

    public ProgrammableHandler(NodeType type,
                               BiFunction<ScenarioNode, ExecutionContext, NodeHandlerResult> behaviour) {
        this.type = type;
        this.behaviour = behaviour;
    }

    /** Always succeeds, following {@code onSuccess}. */
    public static ProgrammableHandler passing(NodeType type) {
        return new ProgrammableHandler(type, (n, c) -> NodeHandlerResult.success(n.onSuccess()));
    }

    @Override public NodeType getSupportedType() { return type; }

    @Override public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) {
        return behaviour.apply(node, ctx);
    }
}
