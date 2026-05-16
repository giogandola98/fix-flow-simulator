package com.fixflow.engine.handlers;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class EndHandler implements NodeHandler {

    @Override
    public NodeType getSupportedType() { return NodeType.END_PASS; }

    @Override
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) {
        return handleAnyEnd(node, ctx);
    }

    public static NodeHandlerResult handleAnyEnd(ScenarioNode node, ExecutionContext ctx) {
        ctx.setStatus(node.type() == NodeType.END_PASS
                ? ExecutionStatus.PASSED
                : ExecutionStatus.FAILED);
        return NodeHandlerResult.terminal();
    }
}
