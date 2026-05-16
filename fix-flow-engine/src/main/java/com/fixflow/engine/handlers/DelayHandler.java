package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class DelayHandler implements NodeHandler {

    @Override
    public NodeType getSupportedType() { return NodeType.DELAY; }

    @Override
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) throws InterruptedException {
        Object raw = node.config().get("delayMs");
        long ms = raw == null ? 0L : ((Number) raw).longValue();
        if (ms > 0) Thread.sleep(ms);
        return NodeHandlerResult.success(node.onSuccess());
    }
}
