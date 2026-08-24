package com.fixflow.engine.handlers;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.scenario.*;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.fix.MessageRouter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

@Component
public class ExpectFIXHandler implements NodeHandler {

    private final CorrelationEngine correlation;
    private final MessageRouter router;

    public ExpectFIXHandler(CorrelationEngine correlation, MessageRouter router) {
        this.correlation = correlation;
        this.router = router;
    }

    @Override
    public NodeType getSupportedType() { return NodeType.EXPECT_FIX; }

    @Override
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) throws InterruptedException {
        try {
            Map<String, Object> cfg = node.config();

            int sourceTag;
            String expectedValue;
            CorrelationRule rule;

            Object corrObj = cfg.get("correlation");
            if (corrObj instanceof Map<?, ?> corr) {
                // Per-node correlation: { sourceTag, fromNode, targetTag }
                int srcTag = corr.get("sourceTag") != null
                        ? ((Number) corr.get("sourceTag")).intValue() : 11;
                String fromNode = corr.get("fromNode") != null ? String.valueOf(corr.get("fromNode")) : null;
                int targetTag = corr.get("targetTag") != null
                        ? ((Number) corr.get("targetTag")).intValue() : srcTag;

                String sentValue = null;
                if (fromNode != null) {
                    Map<Integer, String> sent = ctx.getNodeMessage(fromNode);
                    if (sent != null) sentValue = sent.get(targetTag);
                }

                sourceTag = srcTag;
                expectedValue = sentValue != null ? sentValue : "";
                rule = new CorrelationRule(sourceTag, fromNode != null ? fromNode : node.id(), targetTag, 0);
            } else if (cfg.get("correlationTag") != null) {
                // Legacy flat format
                sourceTag = ((Number) cfg.get("correlationTag")).intValue();
                expectedValue = String.valueOf(cfg.get("expectedValue"));
                rule = new CorrelationRule(sourceTag, node.id(), sourceTag, 0);
            } else {
                // No correlation: match any message by msgType (tag 35)
                String msgType = cfg.get("msgType") != null ? String.valueOf(cfg.get("msgType")) : "";
                sourceTag = 35;
                expectedValue = msgType;
                rule = new CorrelationRule(35, node.id(), 35, 0);
            }

            String sessionIdStr = ctx.sessionId() != null ? ctx.sessionId().toString() : "";
            CompletableFuture<FIXMessageData> future =
                    correlation.register(ctx.executionId().toString(), sessionIdStr, rule, expectedValue);
            if (ctx.sessionId() != null) router.drain(ctx.sessionId().toString());

            long timeoutMs = node.timeout() == null ? 5_000L : node.timeout().toMillis();

            FIXMessageData received = future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            ctx.storeNodeMessage(node.id(), received);
            return NodeHandlerResult.success(node.onSuccess());
        } catch (TimeoutException timeout) {
            correlation.cancel(ctx.executionId().toString());
            return onTimeout(node);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            correlation.cancel(ctx.executionId().toString());
            throw ie;
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
