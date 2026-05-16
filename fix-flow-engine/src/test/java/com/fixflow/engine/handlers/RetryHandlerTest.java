package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.*;
import com.fixflow.engine.execution.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetryHandlerTest {

    private static Scenario scenarioWith(ScenarioNode... extra) {
        ScenarioNode start = new ScenarioNode("start", "start", NodeType.START, Map.of(), null, null, null, null, null);
        List<ScenarioNode> nodes = new java.util.ArrayList<>();
        nodes.add(start);
        for (ScenarioNode n : extra) nodes.add(n);
        return new Scenario(UUID.randomUUID(), "test", null, "1", null, null, null, null, nodes, null, null);
    }

    @Test
    void succeedsBeforeMaxAttempts() throws InterruptedException {
        NodeDispatcher dispatcher = mock(NodeDispatcher.class);
        AtomicInteger calls = new AtomicInteger();
        when(dispatcher.dispatch(any(ScenarioNode.class), any())).thenAnswer(inv -> {
            int n = calls.incrementAndGet();
            return n < 3
                ? NodeHandlerResult.failure("retry", "fail")
                : NodeHandlerResult.success("after");
        });
        ScenarioNode inner = new ScenarioNode("inner", "inner", NodeType.SEND_FIX, Map.of(), null, null, "after", "retry", null);
        Scenario scenario = scenarioWith(inner);
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), scenario, UUID.randomUUID());

        ScenarioNode node = new ScenarioNode("r", "r", NodeType.RETRY,
            Map.of("targetNodeId", "inner"),
            null, new RetryPolicy(3, 1L), "ok", "ko", null);

        RetryHandler h = new RetryHandler(dispatcher);
        NodeHandlerResult r = h.handle(node, ctx);
        assertThat(r.nextNodeId()).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void failsWhenExceedsMaxAttempts() throws InterruptedException {
        NodeDispatcher dispatcher = mock(NodeDispatcher.class);
        when(dispatcher.dispatch(any(ScenarioNode.class), any()))
            .thenReturn(NodeHandlerResult.failure("retry", "x"));

        ScenarioNode inner = new ScenarioNode("inner", "inner", NodeType.SEND_FIX, Map.of(), null, null, "ok", "retry", null);
        Scenario scenario = scenarioWith(inner);
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), scenario, UUID.randomUUID());

        ScenarioNode node = new ScenarioNode("r", "r", NodeType.RETRY,
            Map.of("targetNodeId", "inner"),
            null, new RetryPolicy(2, 1L), "ok", "ko", null);

        RetryHandler h = new RetryHandler(dispatcher);
        NodeHandlerResult r = h.handle(node, ctx);
        assertThat(r.nextNodeId()).isEqualTo("ko");
        assertThat(r.success()).isFalse();
    }

    @Test
    void loopRunsTargetTheConfiguredNumberOfTimes() throws InterruptedException {
        NodeDispatcher dispatcher = mock(NodeDispatcher.class);
        AtomicInteger calls = new AtomicInteger();
        when(dispatcher.dispatch(any(ScenarioNode.class), any())).thenAnswer(inv -> {
            calls.incrementAndGet();
            return NodeHandlerResult.success("body");
        });

        ScenarioNode body = new ScenarioNode("body", "body", NodeType.SEND_FIX, Map.of(), null, null, "body", "fail", null);
        Scenario scenario = scenarioWith(body);
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), scenario, UUID.randomUUID());

        ScenarioNode node = new ScenarioNode("l", "l", NodeType.LOOP,
            Map.of("targetNodeId", "body", "iterations", 4),
            null, null, "done", "fail", null);

        LoopHandler h = new LoopHandler(dispatcher);
        NodeHandlerResult r = h.handle(node, ctx);
        assertThat(r.nextNodeId()).isEqualTo("done");
        assertThat(calls.get()).isEqualTo(4);
    }
}
