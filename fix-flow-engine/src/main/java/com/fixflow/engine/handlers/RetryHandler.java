package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.RetryPolicy;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class RetryHandler implements NodeHandler {

    private final NodeDispatcher dispatcher;

    public RetryHandler(@Lazy NodeDispatcher dispatcher) { this.dispatcher = dispatcher; }

    @Override
    public NodeType getSupportedType() { return NodeType.RETRY; }

    @Override
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) throws InterruptedException {
        String targetId = (String) node.config().get("targetNodeId");
        if (targetId == null) {
            return NodeHandlerResult.failure(node.onFailure(), "missing targetNodeId");
        }
        ScenarioNode target = ctx.scenario().findNode(targetId)
            .orElseThrow(() -> new IllegalStateException("node not found: " + targetId));

        RetryPolicy policy = node.retryPolicy() != null ? node.retryPolicy() : new RetryPolicy(1, 0L);
        int max = policy.maxAttempts();
        NodeHandlerResult last = NodeHandlerResult.failure(node.onFailure(), "no attempts");

        for (int attempt = 1; attempt <= max; attempt++) {
            last = dispatcher.dispatch(target, ctx);
            if (last.success()) {
                return NodeHandlerResult.success(node.onSuccess());
            }
            if (attempt < max && policy.delayMs() > 0) {
                Thread.sleep(policy.delayMs());
            }
        }
        return NodeHandlerResult.failure(node.onFailure(),
            "exhausted " + max + " retries: " + last.errorMessage());
    }
}
