package com.fixflow.engine.handlers;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.NodeType;
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

class LoopHandlerTest {

    private final AtomicInteger counter = new AtomicInteger();

    private NodeDispatcher bodyDispatcher() {
        ProgrammableHandler counting = new ProgrammableHandler(NodeType.SEND_FIX, (n, c) -> {
            counter.incrementAndGet();
            return NodeHandlerResult.success(n.onSuccess());
        });
        return new NodeDispatcher(List.of(new StartHandler(), new EndHandler(), counting));
    }

    @Test
    void supportsLoop() {
        assertThat(new LoopHandler(bodyDispatcher()).getSupportedType()).isEqualTo(NodeType.LOOP);
    }

    @Test
    void missingTargetNodeIdRoutesOnFailure() throws Exception {
        LoopHandler loop = new LoopHandler(new NodeDispatcher(List.of()));
        Scenario s = scenario("s", start("loop"),
                node("loop", NodeType.LOOP).onSuccess("ok").onFailure("no").build());
        NodeHandlerResult r = loop.handle(s.findNode("loop").get(), Fixtures.ctx(s));
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("no");
        assertThat(r.errorMessage()).isEqualTo("missing targetNodeId");
    }

    @Test
    void unknownTargetNodeThrows() {
        LoopHandler loop = new LoopHandler(bodyDispatcher());
        Scenario s = scenario("s", start("loop"),
                node("loop", NodeType.LOOP).cfg("targetNodeId", "ghost").onSuccess("ok").build());
        assertThatThrownBy(() -> loop.handle(s.findNode("loop").get(), Fixtures.ctx(s)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void walksFullSubBlockEachIteration() throws Exception {
        LoopHandler loop = new LoopHandler(bodyDispatcher());
        Scenario s = scenario("s", start("loop"),
                node("loop", NodeType.LOOP).cfg("targetNodeId", "a").cfg("iterations", 2)
                        .onSuccess("ok").onFailure("no").build(),
                node("a", NodeType.SEND_FIX).onSuccess("b").build(),
                node("b", NodeType.SEND_FIX).onSuccess("loop").build()); // back to boundary
        NodeHandlerResult r = loop.handle(s.findNode("loop").get(), Fixtures.ctx(s));
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("ok");
        assertThat(counter).as("2 nodes * 2 iterations").hasValue(4);
    }

    @Test
    void defaultsToSingleIteration() throws Exception {
        LoopHandler loop = new LoopHandler(bodyDispatcher());
        Scenario s = scenario("s", start("loop"),
                node("loop", NodeType.LOOP).cfg("targetNodeId", "a").onSuccess("ok").build(),
                node("a", NodeType.SEND_FIX).onSuccess("loop").build());
        loop.handle(s.findNode("loop").get(), Fixtures.ctx(s));
        assertThat(counter).hasValue(1);
    }

    @Test
    void endNodeInsideBodyPropagatesTerminal() throws Exception {
        LoopHandler loop = new LoopHandler(bodyDispatcher());
        Scenario s = scenario("s", start("loop"),
                node("loop", NodeType.LOOP).cfg("targetNodeId", "a").cfg("iterations", 5)
                        .onSuccess("ok").build(),
                node("a", NodeType.SEND_FIX).onSuccess("end").build(),
                Fixtures.endPass("end"));
        ExecutionContext ctx = Fixtures.ctx(s);
        NodeHandlerResult r = loop.handle(s.findNode("loop").get(), ctx);
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isNull();
        assertThat(ctx.status()).isEqualTo(ExecutionStatus.PASSED);
    }

    @Test
    void failingBodyRoutesOnFailure() throws Exception {
        ProgrammableHandler failing = new ProgrammableHandler(NodeType.SEND_FIX,
                (n, c) -> NodeHandlerResult.failure(null, "bad"));
        LoopHandler loop = new LoopHandler(new NodeDispatcher(List.of(new StartHandler(), failing)));
        Scenario s = scenario("s", start("loop"),
                node("loop", NodeType.LOOP).cfg("targetNodeId", "a").cfg("iterations", 3)
                        .onSuccess("ok").onFailure("no").build(),
                node("a", NodeType.SEND_FIX).build());
        NodeHandlerResult r = loop.handle(s.findNode("loop").get(), Fixtures.ctx(s));
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("no");
        assertThat(r.errorMessage()).contains("loop iteration 0 failed");
    }
}
