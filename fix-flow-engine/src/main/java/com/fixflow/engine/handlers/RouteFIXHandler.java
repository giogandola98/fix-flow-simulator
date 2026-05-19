package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.core.domain.scenario.TimeoutAction;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.fix.MessageRouter;
import com.fixflow.engine.variable.VariableResolver;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

@Component
public class RouteFIXHandler implements NodeHandler {

    private final CorrelationEngine correlation;
    private final VariableResolver resolver;
    private final MessageRouter router;

    public RouteFIXHandler(CorrelationEngine correlation, VariableResolver resolver, MessageRouter router) {
        this.correlation = correlation;
        this.resolver = resolver;
        this.router = router;
    }

    @Override
    public NodeType getSupportedType() { return NodeType.ROUTE_FIX; }

    @Override
    @SuppressWarnings("unchecked")
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) throws InterruptedException {
        Map<String, Object> cfg = node.config();
        List<Map<String, Object>> rawRules = (List<Map<String, Object>>) cfg.getOrDefault("rules", List.of());

        String execId = ctx.executionId().toString() + ":route:" + node.id();
        long timeoutMs = node.timeout() == null ? 30_000L : node.timeout().toMillis();

        try {
            List<CorrelationEngine.RoutingRule> rules = new ArrayList<>();
            for (Map<String, Object> r : rawRules) {
                String ruleId       = Objects.toString(r.get("ruleId"), UUID.randomUUID().toString());
                String label        = Objects.toString(r.getOrDefault("label", ""), "");
                String targetNodeId = Objects.toString(r.get("targetNodeId"), "");
                Map<Integer, String> matchers = new LinkedHashMap<>();
                Object matchersObj = r.get("matchers");
                if (matchersObj instanceof Map<?,?> mm) {
                    for (Map.Entry<?,?> e : mm.entrySet()) {
                        String resolved = resolver.resolveAll(Objects.toString(e.getValue(), ""), ctx);
                        matchers.put(Integer.parseInt(e.getKey().toString()), resolved);
                    }
                }
                rules.add(new CorrelationEngine.RoutingRule(ruleId, label, matchers, targetNodeId));
            }

            CompletableFuture<CorrelationEngine.RoutedResult> future = correlation.registerMulti(execId, rules);
            if (ctx.sessionId() != null) router.drain(ctx.sessionId().toString());

            CorrelationEngine.RoutedResult result =
                    future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            ctx.storeNodeMessage(node.id(), result.fields());
            ctx.setVariable("node:" + node.id() + ":matchedRuleId", result.matchedRuleId());
            String matchedLabel = rules.stream()
                    .filter(rl -> rl.ruleId().equals(result.matchedRuleId()))
                    .map(CorrelationEngine.RoutingRule::label)
                    .findFirst().orElse(result.matchedRuleId());
            ctx.setVariable("node:" + node.id() + ":matchedRuleLabel", matchedLabel);
            return NodeHandlerResult.success(result.targetNodeId());
        } catch (TimeoutException timeout) {
            correlation.cancelMulti(execId);
            return onTimeout(node);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            correlation.cancelMulti(execId);
            throw ie;
        } catch (Exception other) {
            correlation.cancelMulti(execId);
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
