package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.*;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class RouteFIXHandlerTest {

    private static ExecutionContext freshCtx() {
        Scenario s = new Scenario(UUID.randomUUID(), "t", "", "1.0", "s",
                RuntimePolicy.PARALLEL, List.of(), List.of(), List.of(), List.of(), Map.of());
        return new ExecutionContext(UUID.randomUUID(), s, UUID.randomUUID());
    }

    @Test
    void staticMatcherRoutesToCorrectTarget() throws Exception {
        CorrelationEngine correlation = new CorrelationEngine();
        RouteFIXHandler handler = new RouteFIXHandler(correlation, new VariableResolver());
        ExecutionContext ctx = freshCtx();

        Map<String, Object> rule = Map.of(
            "ruleId", "r1", "label", "Quote",
            "matchers", Map.of("35", "S"),
            "targetNodeId", "quote-node");
        ScenarioNode node = new ScenarioNode("route1", "Router", NodeType.ROUTE_FIX,
            Map.of("rules", List.of(rule)),
            new TimeoutConfig(500, TimeUnit.MILLISECONDS, TimeoutAction.FAIL, null),
            null, null, "fail", null);

        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            correlation.onMessage("sess", Map.of(35, "S", 131, "RFQ-001"));
        });

        NodeHandlerResult r = handler.handle(node, ctx);
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("quote-node");
        assertThat(ctx.getVariable("node:route1:matchedRuleLabel")).isEqualTo("Quote");
    }

    @Test
    void placeholderMatcherValueResolvedBeforeMatching() throws Exception {
        CorrelationEngine correlation = new CorrelationEngine();
        RouteFIXHandler handler = new RouteFIXHandler(correlation, new VariableResolver());
        ExecutionContext ctx = freshCtx();
        ctx.storeNodeMessage("send-rfq", Map.of(131, "RFQ-001"));

        Map<String, Object> rule = Map.of(
            "ruleId", "r1", "label", "Matched RFQ",
            "matchers", Map.of("131", "{{node:send-rfq:tag131}}"),
            "targetNodeId", "process-node");
        ScenarioNode node = new ScenarioNode("route1", "Router", NodeType.ROUTE_FIX,
            Map.of("rules", List.of(rule)),
            new TimeoutConfig(500, TimeUnit.MILLISECONDS, TimeoutAction.FAIL, null),
            null, null, "fail", null);

        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            correlation.onMessage("sess", Map.of(131, "RFQ-001", 35, "S"));
        });

        NodeHandlerResult r = handler.handle(node, ctx);
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("process-node");
    }

    @Test
    void defaultRuleUsedWhenNoMatchersFire() throws Exception {
        CorrelationEngine correlation = new CorrelationEngine();
        RouteFIXHandler handler = new RouteFIXHandler(correlation, new VariableResolver());
        ExecutionContext ctx = freshCtx();

        Map<String, Object> specificRule = Map.of(
            "ruleId", "r1", "label", "Quote",
            "matchers", Map.of("35", "S"),
            "targetNodeId", "quote-node");
        Map<String, Object> defaultRule = Map.of(
            "ruleId", "r2", "label", "Default",
            "matchers", Map.of(),
            "targetNodeId", "default-node");
        ScenarioNode node = new ScenarioNode("route1", "Router", NodeType.ROUTE_FIX,
            Map.of("rules", List.of(specificRule, defaultRule)),
            new TimeoutConfig(500, TimeUnit.MILLISECONDS, TimeoutAction.FAIL, null),
            null, null, "fail", null);

        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            correlation.onMessage("sess", Map.of(35, "AG"));
        });

        NodeHandlerResult r = handler.handle(node, ctx);
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("default-node");
        assertThat(ctx.getVariable("node:route1:matchedRuleLabel")).isEqualTo("Default");
    }

    @Test
    void timeoutYieldsFailure() throws Exception {
        CorrelationEngine correlation = new CorrelationEngine();
        RouteFIXHandler handler = new RouteFIXHandler(correlation, new VariableResolver());
        ExecutionContext ctx = freshCtx();

        Map<String, Object> rule = Map.of(
            "ruleId", "r1", "label", "Quote",
            "matchers", Map.of("35", "S"),
            "targetNodeId", "quote-node");
        ScenarioNode node = new ScenarioNode("route1", "Router", NodeType.ROUTE_FIX,
            Map.of("rules", List.of(rule)),
            new TimeoutConfig(100, TimeUnit.MILLISECONDS, TimeoutAction.FAIL, null),
            null, null, "fail", null);

        NodeHandlerResult r = handler.handle(node, ctx);
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("fail");
    }
}
