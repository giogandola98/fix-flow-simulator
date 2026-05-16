package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class WaitHandler implements NodeHandler {

    @Override
    public NodeType getSupportedType() { return NodeType.WAIT; }

    @Override
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) throws InterruptedException {
        long ms = node.timeout() == null ? 0L : node.timeout().toMillis();
        if (ms > 0) Thread.sleep(ms);
        return NodeHandlerResult.success(node.onSuccess());
    }
}
