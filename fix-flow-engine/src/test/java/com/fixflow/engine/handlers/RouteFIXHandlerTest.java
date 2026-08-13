package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.core.domain.scenario.TimeoutAction;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.fix.MessageBuffer;
import com.fixflow.engine.fix.MessageRouter;
import com.fixflow.engine.support.Fixtures;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.fixflow.engine.support.Fixtures.node;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static com.fixflow.engine.support.Fixtures.timeout;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RouteFIXHandlerTest {

    private CorrelationEngine correlation;
    private RouteFIXHandler handler;
    private UUID sessionId;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    @BeforeEach
    void setUp() {
        correlation = new CorrelationEngine();
        MessageRouter router = new MessageRouter(correlation, new MessageBuffer());
        handler = new RouteFIXHandler(correlation, new VariableResolver(), router);
        sessionId = UUID.randomUUID();
    }

    private ExecutionContext ctx() { return Fixtures.ctx(scenario("s", start("route")), sessionId); }

    private ScenarioNode routeNode(Object timeoutCfg) {
        List<Map<String, Object>> rules = List.of(
                Map.of("ruleId", "r1", "label", "typeD", "matchers", Map.of("35", "D"), "targetNodeId", "t1"),
                Map.of("ruleId", "r2", "label", "typeEight", "matchers", Map.of("35", "8"), "targetNodeId", "t2"));
        Fixtures.NodeBuilder b = node("route", NodeType.ROUTE_FIX).cfg("rules", rules)
                .onSuccess("ok").onFailure("no");
        if (timeoutCfg instanceof com.fixflow.core.domain.scenario.TimeoutConfig t) b.timeout(t);
        return b.build();
    }

    @Test
    void supportsRouteFix() {
        assertThat(handler.getSupportedType()).isEqualTo(NodeType.ROUTE_FIX);
    }

    @Test
    void routesToMatchingRuleTarget() throws Exception {
        ExecutionContext ctx = ctx();
        ScenarioNode route = routeNode(null);
        Future<NodeHandlerResult> f = pool.submit(() -> handler.handle(route, ctx));
        await().atMost(Duration.ofSeconds(3)).until(() -> correlation.pendingCount() > 0);
        correlation.onMessage(sessionId.toString(), Fixtures.fields(35, "8", 11, "X"));
        NodeHandlerResult r = f.get(3, TimeUnit.SECONDS);

        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("t2");
        assertThat(ctx.getVariable("node:route:matchedRuleId")).isEqualTo("r2");
        assertThat(ctx.getVariable("node:route:matchedRuleLabel")).isEqualTo("typeEight");
        assertThat(ctx.getNodeMessage("route")).containsEntry(35, "8");
    }

    @Test
    void defaultRuleMatchesWhenNoSpecificRuleMatches() throws Exception {
        ExecutionContext ctx = ctx();
        List<Map<String, Object>> rules = List.of(
                Map.of("ruleId", "r1", "matchers", Map.of("35", "D"), "targetNodeId", "t1"),
                Map.of("ruleId", "def", "label", "fallback", "matchers", Map.of(), "targetNodeId", "tDefault"));
        ScenarioNode route = node("route", NodeType.ROUTE_FIX).cfg("rules", rules).onSuccess("ok").onFailure("no").build();
        Future<NodeHandlerResult> f = pool.submit(() -> handler.handle(route, ctx));
        await().atMost(Duration.ofSeconds(3)).until(() -> correlation.pendingCount() > 0);
        correlation.onMessage(sessionId.toString(), Fixtures.fields(35, "Z"));
        NodeHandlerResult r = f.get(3, TimeUnit.SECONDS);
        assertThat(r.nextNodeId()).isEqualTo("tDefault");
    }

    @Test
    void timeoutFailRoutesOnFailure() throws Exception {
        NodeHandlerResult r = handler.handle(routeNode(timeout(30, TimeoutAction.FAIL, null)), ctx());
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("no");
        assertThat(r.errorMessage()).isEqualTo("timeout");
    }

    @Test
    void timeoutContinueRoutesOnSuccess() throws Exception {
        NodeHandlerResult r = handler.handle(routeNode(timeout(30, TimeoutAction.CONTINUE, null)), ctx());
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("ok");
    }

    @Test
    void timeoutJumpRoutesToJumpTarget() throws Exception {
        NodeHandlerResult r = handler.handle(routeNode(timeout(30, TimeoutAction.JUMP, "elsewhere")), ctx());
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("elsewhere");
    }
}
