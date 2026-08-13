package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.RetryPolicy;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.support.Fixtures;
import com.fixflow.engine.support.ProgrammableHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.fixflow.engine.support.Fixtures.node;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryHandlerTest {

    private final AtomicInteger attempts = new AtomicInteger();

    private RetryHandler handlerFailingFor(int failFirstN) {
        ProgrammableHandler flaky = new ProgrammableHandler(NodeType.SEND_FIX, (n, c) -> {
            int a = attempts.incrementAndGet();
            return a <= failFirstN
                    ? NodeHandlerResult.failure(n.onFailure(), "attempt " + a + " failed")
                    : NodeHandlerResult.success(n.onSuccess());
        });
        return new RetryHandler(new NodeDispatcher(List.of(flaky)));
    }

    private Scenario scenarioWith(RetryPolicy policy, String targetId) {
        return scenario("s", start("r"),
                node("r", NodeType.RETRY).cfg("targetNodeId", targetId).retry(policy)
                        .onSuccess("ok").onFailure("no").build(),
                node("target", NodeType.SEND_FIX).onSuccess("inner").onFailure("innerFail").build());
    }

    @Test
    void supportsRetry() {
        assertThat(handlerFailingFor(0).getSupportedType()).isEqualTo(NodeType.RETRY);
    }

    @Test
    void succeedsBeforeMaxAttempts() throws Exception {
        RetryHandler h = handlerFailingFor(1); // fail once then pass
        Scenario s = scenarioWith(new RetryPolicy(3, 0L), "target");
        NodeHandlerResult r = h.handle(s.findNode("r").get(), Fixtures.ctx(s));
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("ok");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void exhaustsAllAttemptsThenFails() throws Exception {
        RetryHandler h = handlerFailingFor(99); // always fails
        Scenario s = scenarioWith(new RetryPolicy(2, 0L), "target");
        NodeHandlerResult r = h.handle(s.findNode("r").get(), Fixtures.ctx(s));
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("no");
        assertThat(r.errorMessage()).contains("exhausted 2 retries");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void defaultsToSingleAttemptWhenPolicyNull() throws Exception {
        RetryHandler h = handlerFailingFor(99);
        Scenario s = scenarioWith(null, "target");
        NodeHandlerResult r = h.handle(s.findNode("r").get(), Fixtures.ctx(s));
        assertThat(r.success()).isFalse();
        assertThat(attempts).hasValue(1);
    }

    @Test
    void missingTargetNodeIdRoutesOnFailure() throws Exception {
        RetryHandler h = handlerFailingFor(0);
        Scenario s = scenario("s", start("r"),
                node("r", NodeType.RETRY).onSuccess("ok").onFailure("no").build());
        NodeHandlerResult r = h.handle(s.findNode("r").get(), Fixtures.ctx(s));
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("no");
        assertThat(r.errorMessage()).isEqualTo("missing targetNodeId");
    }

    @Test
    void unknownTargetNodeThrows() {
        RetryHandler h = handlerFailingFor(0);
        Scenario s = scenarioWith(new RetryPolicy(1, 0L), "ghost");
        ExecutionContext ctx = Fixtures.ctx(s);
        assertThatThrownBy(() -> h.handle(s.findNode("r").get(), ctx))
                .isInstanceOf(IllegalStateException.class);
    }
}
