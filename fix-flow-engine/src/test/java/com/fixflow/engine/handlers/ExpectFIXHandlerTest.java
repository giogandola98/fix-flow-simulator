package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.*;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.fix.MessageBuffer;
import com.fixflow.engine.fix.MessageRouter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class ExpectFIXHandlerTest {

    @Test
    void returnsSuccessWhenMatchingMessageArrives() throws Exception {
        CorrelationEngine engine = new CorrelationEngine();
        MessageRouter router = new MessageRouter(engine, new MessageBuffer());
        ExpectFIXHandler handler = new ExpectFIXHandler(engine, router);

        ScenarioNode node = new ScenarioNode(
                "n3", "expect", NodeType.EXPECT_FIX,
                Map.of("correlationTag", 131, "expectedValue", "REQ-1"),
                new TimeoutConfig(2, TimeUnit.SECONDS, TimeoutAction.FAIL, null),
                null, "n4", null, null);

        Scenario s = new Scenario(UUID.randomUUID(), "demo", "", "1.0", "sess",
                RuntimePolicy.PARALLEL, List.of(),
                List.of(new CorrelationRule(131, "n3", 131, 2000)),
                List.of(node), List.of(), Map.of());
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), s, UUID.randomUUID());

        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            engine.onMessage("sess", Map.of(131, "REQ-1", 35, "8"));
        });

        NodeHandlerResult result = handler.handle(node, ctx);

        assertThat(result.success()).isTrue();
        assertThat(result.nextNodeId()).isEqualTo("n4");
        assertThat(ctx.getNodeMessage("n3")).containsEntry(131, "REQ-1");
    }

    @Test
    void returnsFailureOnTimeoutWithFailAction() throws Exception {
        CorrelationEngine engine = new CorrelationEngine();
        MessageRouter router = new MessageRouter(engine, new MessageBuffer());
        ExpectFIXHandler handler = new ExpectFIXHandler(engine, router);

        ScenarioNode node = new ScenarioNode(
                "n3", "expect", NodeType.EXPECT_FIX,
                Map.of("correlationTag", 131, "expectedValue", "REQ-X"),
                new TimeoutConfig(100, TimeUnit.MILLISECONDS, TimeoutAction.FAIL, null),
                null, "n4", "nf", null);

        Scenario s = new Scenario(UUID.randomUUID(), "demo", "", "1.0", "sess",
                RuntimePolicy.PARALLEL, List.of(),
                List.of(new CorrelationRule(131, "n3", 131, 100)),
                List.of(node), List.of(), Map.of());
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), s, UUID.randomUUID());

        NodeHandlerResult result = handler.handle(node, ctx);

        assertThat(result.success()).isFalse();
        assertThat(result.nextNodeId()).isEqualTo("nf");
    }
}
