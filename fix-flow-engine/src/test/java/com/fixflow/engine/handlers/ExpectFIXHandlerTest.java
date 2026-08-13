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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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

class ExpectFIXHandlerTest {

    private CorrelationEngine correlation;
    private MessageRouter router;
    private ExpectFIXHandler handler;
    private UUID sessionId;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    @BeforeEach
    void setUp() {
        correlation = new CorrelationEngine();
        router = new MessageRouter(correlation, new MessageBuffer());
        handler = new ExpectFIXHandler(correlation, router);
        sessionId = UUID.randomUUID();
    }

    private ExecutionContext ctx(Scenario s) { return Fixtures.ctx(s, sessionId); }

    private NodeHandlerResult runAndInject(ScenarioNode node, ExecutionContext ctx, Map<Integer, String> inbound)
            throws Exception {
        Scenario s = scenario("s", start(node.id()), node);
        Future<NodeHandlerResult> f = pool.submit(() -> handler.handle(node, ctx));
        await().atMost(Duration.ofSeconds(3)).until(() -> correlation.pendingCount() > 0);
        correlation.onMessage(sessionId.toString(), inbound);
        return f.get(3, TimeUnit.SECONDS);
    }

    @Test
    void supportsExpectFix() {
        assertThat(handler.getSupportedType()).isEqualTo(NodeType.EXPECT_FIX);
    }

    @Test
    void correlationBlockMatchesSentValue() throws Exception {
        Scenario s = scenario("s", start("exp"));
        ExecutionContext ctx = ctx(s);
        ctx.storeNodeMessage("send", Fixtures.fields(11, "ORD1"));
        ScenarioNode exp = node("exp", NodeType.EXPECT_FIX)
                .cfg("correlation", Map.of("sourceTag", 11, "fromNode", "send", "targetTag", 11))
                .onSuccess("done").onFailure("fail").build();
        NodeHandlerResult r = runAndInject(exp, ctx, Fixtures.fields(11, "ORD1", 35, "8"));
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("done");
        assertThat(ctx.getNodeMessage("exp")).containsEntry(11, "ORD1");
    }

    @Test
    void legacyCorrelationTagMatches() throws Exception {
        ScenarioNode exp = node("exp", NodeType.EXPECT_FIX)
                .cfg("correlationTag", 11).cfg("expectedValue", "ORD1")
                .onSuccess("done").onFailure("fail").build();
        NodeHandlerResult r = runAndInject(exp, ctx(scenario("s", start("exp"))), Fixtures.fields(11, "ORD1"));
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("done");
    }

    @Test
    void msgTypeFallbackMatchesTag35() throws Exception {
        ScenarioNode exp = node("exp", NodeType.EXPECT_FIX).cfg("msgType", "8")
                .onSuccess("done").onFailure("fail").build();
        NodeHandlerResult r = runAndInject(exp, ctx(scenario("s", start("exp"))), Fixtures.fields(35, "8", 11, "X"));
        assertThat(r.success()).isTrue();
    }

    @Test
    void timeoutFailRoutesOnFailure() throws Exception {
        ScenarioNode exp = node("exp", NodeType.EXPECT_FIX).cfg("msgType", "8")
                .timeout(timeout(30, TimeoutAction.FAIL, null)).onSuccess("done").onFailure("fail").build();
        NodeHandlerResult r = handler.handle(exp, ctx(scenario("s", start("exp"))));
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("fail");
        assertThat(r.errorMessage()).isEqualTo("timeout");
    }

    @Test
    void timeoutContinueRoutesOnSuccess() throws Exception {
        ScenarioNode exp = node("exp", NodeType.EXPECT_FIX).cfg("msgType", "8")
                .timeout(timeout(30, TimeoutAction.CONTINUE, null)).onSuccess("done").onFailure("fail").build();
        NodeHandlerResult r = handler.handle(exp, ctx(scenario("s", start("exp"))));
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("done");
    }

    @Test
    void timeoutRetryExhaustedRoutesOnFailure() throws Exception {
        ScenarioNode exp = node("exp", NodeType.EXPECT_FIX).cfg("msgType", "8")
                .timeout(timeout(30, TimeoutAction.RETRY, null)).onSuccess("done").onFailure("fail").build();
        NodeHandlerResult r = handler.handle(exp, ctx(scenario("s", start("exp"))));
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("fail");
        assertThat(r.errorMessage()).isEqualTo("timeout-retry-exhausted");
    }

    @Test
    void timeoutJumpRoutesToJumpTarget() throws Exception {
        ScenarioNode exp = node("exp", NodeType.EXPECT_FIX).cfg("msgType", "8")
                .timeout(timeout(30, TimeoutAction.JUMP, "jumpTarget")).onSuccess("done").onFailure("fail").build();
        NodeHandlerResult r = handler.handle(exp, ctx(scenario("s", start("exp"))));
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("jumpTarget");
    }

    @Test
    void badConfigRoutesOnFailure() throws Exception {
        // sourceTag as a String triggers a ClassCastException before registering -> onFailure
        ScenarioNode exp = node("exp", NodeType.EXPECT_FIX)
                .cfg("correlation", Map.of("sourceTag", "not-a-number"))
                .onSuccess("done").onFailure("fail").build();
        NodeHandlerResult r = handler.handle(exp, ctx(scenario("s", start("exp"))));
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("fail");
    }
}
