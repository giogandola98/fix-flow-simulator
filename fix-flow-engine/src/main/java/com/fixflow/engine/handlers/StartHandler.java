package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class StartHandler implements NodeHandler {
    @Override public NodeType getSupportedType() { return NodeType.START; }
    @Override public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) {
        return NodeHandlerResult.success(node.onSuccess());
    }
}
