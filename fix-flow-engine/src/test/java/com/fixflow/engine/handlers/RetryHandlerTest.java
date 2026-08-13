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
        return new Scenario(UUID.randomUUID(), "test", null, "1", null, null, null, null, nodes, null, null, null);
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
    void loopWalksEntireSubBlockEachIteration() throws InterruptedException {
        // #62: each iteration must walk the whole block (a -> b), not just the attached node.
        NodeDispatcher dispatcher = mock(NodeDispatcher.class);
        Map<String, Integer> calls = new java.util.HashMap<>();
        when(dispatcher.dispatch(any(ScenarioNode.class), any())).thenAnswer(inv -> {
            ScenarioNode n = inv.getArgument(0);
            calls.merge(n.id(), 1, Integer::sum);
            return NodeHandlerResult.success(n.onSuccess());
        });

        // a -> b -> back to the LOOP node "l" (the loop-back boundary).
        ScenarioNode a = new ScenarioNode("a", "a", NodeType.SEND_FIX, Map.of(), null, null, "b", "fail", null);
        ScenarioNode b = new ScenarioNode("b", "b", NodeType.SEND_FIX, Map.of(), null, null, "l", "fail", null);
        Scenario scenario = scenarioWith(a, b);
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), scenario, UUID.randomUUID());

        ScenarioNode node = new ScenarioNode("l", "l", NodeType.LOOP,
            Map.of("targetNodeId", "a", "iterations", 3),
            null, null, "done", "fail", null);

        LoopHandler h = new LoopHandler(dispatcher);
        NodeHandlerResult r = h.handle(node, ctx);

        assertThat(r.nextNodeId()).isEqualTo("done");
        assertThat(calls.get("a")).isEqualTo(3);
        assertThat(calls.get("b")).isEqualTo(3);
    }
}
