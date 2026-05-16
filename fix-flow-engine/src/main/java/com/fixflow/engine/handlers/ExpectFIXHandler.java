package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.*;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

@Component
public class ExpectFIXHandler implements NodeHandler {

    private final CorrelationEngine correlation;

    public ExpectFIXHandler(CorrelationEngine correlation) { this.correlation = correlation; }

    @Override
    public NodeType getSupportedType() { return NodeType.EXPECT_FIX; }

    @Override
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) throws InterruptedException {
        Map<String, Object> cfg = node.config();
        int tag = ((Number) cfg.get("correlationTag")).intValue();
        String expected = String.valueOf(cfg.get("expectedValue"));

        CorrelationRule rule = ctx.scenario().correlationRules().stream()
                .filter(r -> r.sourceTag() == tag)
                .findFirst()
                .orElse(new CorrelationRule(tag, node.id(), tag, 0));

        CompletableFuture<Map<Integer, String>> future =
                correlation.register(ctx.executionId().toString(), rule, expected);

        long timeoutMs = node.timeout() == null ? 5_000L : node.timeout().toMillis();

        try {
            Map<Integer, String> fields = future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            ctx.storeNodeMessage(node.id(), fields);
            return NodeHandlerResult.success(node.onSuccess());
        } catch (TimeoutException timeout) {
            correlation.cancel(ctx.executionId().toString());
            return onTimeout(node);
        } catch (Exception other) {
            correlation.cancel(ctx.executionId().toString());
            return NodeHandlerResult.failure(node.onFailure(), other.getMessage());
        }
    }

    private NodeHandlerResult onTimeout(ScenarioNode node) {
        TimeoutAction action = node.timeout() == null ? TimeoutAction.FAIL : node.timeout().onTimeout();
        return switch (action) {
            case FAIL     -> NodeHandlerResult.failure(node.onFailure(), "timeout");
            case CONTINUE -> NodeHandlerResult.success(node.onSuccess());
            case RETRY    -> NodeHandlerResult.failure(node.onFailure(), "timeout-retry-exhausted");
            case JUMP     -> NodeHandlerResult.success(node.timeout().jumpTo());
        };
    }
}
