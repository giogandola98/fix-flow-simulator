package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.*;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionHandlerTest {

    private final VariableResolver resolver = new VariableResolver();

    private static ExecutionContext freshCtx() {
        Scenario s = new Scenario(UUID.randomUUID(), "t", "", "1.0", "s",
                RuntimePolicy.PARALLEL, List.of(), List.of(), List.of(), List.of(), Map.of(), null);
        return new ExecutionContext(UUID.randomUUID(), s, UUID.randomUUID());
    }

    @Test
    void decisionGoesOnSuccessWhenConditionTrue() throws Exception {
        DecisionHandler h = new DecisionHandler(resolver);
        ExecutionContext ctx = freshCtx();
        ctx.storeNodeMessage("n1", Map.of(39, "2"));
        ScenarioNode node = new ScenarioNode("d", "decide", NodeType.DECISION,
            Map.of("condition", "{{node:n1:tag39}} == 2"),
            null, null, "yes", "no", null);
        NodeHandlerResult r = h.handle(node, ctx);
        assertThat(r.nextNodeId()).isEqualTo("yes");
        assertThat(r.success()).isTrue();
    }

    @Test
    void decisionGoesOnFailureWhenConditionFalse() throws Exception {
        DecisionHandler h = new DecisionHandler(resolver);
        ExecutionContext ctx = freshCtx();
        ctx.storeNodeMessage("n1", Map.of(39, "1"));
        ScenarioNode node = new ScenarioNode("d", "decide", NodeType.DECISION,
            Map.of("condition", "{{node:n1:tag39}} == 2"),
            null, null, "yes", "no", null);
        NodeHandlerResult r = h.handle(node, ctx);
        assertThat(r.nextNodeId()).isEqualTo("no");
        assertThat(r.success()).isFalse();
    }

    @Test
    void waitBlocksForConfiguredDuration() throws Exception {
        WaitHandler h = new WaitHandler();
        ExecutionContext ctx = freshCtx();
        ScenarioNode node = new ScenarioNode("w", "wait", NodeType.WAIT,
            Map.of(),
            new TimeoutConfig(50, TimeUnit.MILLISECONDS, TimeoutAction.FAIL, null),
            null, "next", "fail", null);
        long start = System.nanoTime();
        NodeHandlerResult r = h.handle(node, ctx);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertThat(elapsedMs).isGreaterThanOrEqualTo(50L);
        assertThat(r.nextNodeId()).isEqualTo("next");
    }

    @Test
    void delayBlocksForConfiguredMs() throws Exception {
        DelayHandler h = new DelayHandler();
        ExecutionContext ctx = freshCtx();
        ScenarioNode node = new ScenarioNode("d", "delay", NodeType.DELAY,
            Map.of("delayMs", 50),
            null, null, "next", "fail", null);
        long start = System.nanoTime();
        NodeHandlerResult r = h.handle(node, ctx);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertThat(elapsedMs).isGreaterThanOrEqualTo(50L);
        assertThat(r.nextNodeId()).isEqualTo("next");
    }
}
