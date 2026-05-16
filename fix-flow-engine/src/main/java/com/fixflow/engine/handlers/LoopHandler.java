package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class LoopHandler implements NodeHandler {

    private final NodeDispatcher dispatcher;

    public LoopHandler(NodeDispatcher dispatcher) { this.dispatcher = dispatcher; }

    @Override
    public NodeType getSupportedType() { return NodeType.LOOP; }

    @Override
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) throws InterruptedException {
        String targetId = (String) node.config().get("targetNodeId");
        if (targetId == null) {
            return NodeHandlerResult.failure(node.onFailure(), "missing targetNodeId");
        }
        ScenarioNode target = ctx.scenario().findNode(targetId)
            .orElseThrow(() -> new IllegalStateException("node not found: " + targetId));

        int iterations = node.config().get("iterations") == null
            ? 1
            : ((Number) node.config().get("iterations")).intValue();

        for (int i = 0; i < iterations; i++) {
            NodeHandlerResult r = dispatcher.dispatch(target, ctx);
            if (!r.success()) {
                return NodeHandlerResult.failure(node.onFailure(),
                    "loop iteration " + i + " failed: " + r.errorMessage());
            }
        }
        return NodeHandlerResult.success(node.onSuccess());
    }
}
