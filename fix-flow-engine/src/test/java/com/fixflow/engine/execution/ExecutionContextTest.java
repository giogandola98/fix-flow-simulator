package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.ExecutionEventType;
import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.handlers.NodeHandlerResult;
import com.fixflow.engine.support.Fixtures;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.fixflow.engine.support.Fixtures.node;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static org.assertj.core.api.Assertions.assertThat;

class ExecutionContextTest {

    private ExecutionContext newCtx() {
        Scenario s = scenario("s", start("end"), Fixtures.endPass("end"));
        return new ExecutionContext(UUID.randomUUID(), s, UUID.randomUUID());
    }

    @Test
    void startsRunningWithStartTimeSet() {
        ExecutionContext ctx = newCtx();
        assertThat(ctx.status()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(ctx.startTime()).isNotNull();
        assertThat(ctx.executionId()).isNotNull();
        assertThat(ctx.scenario()).isNotNull();
        assertThat(ctx.sessionId()).isNotNull();
    }

    @Test
    void storesAndReadsVariables() {
        ExecutionContext ctx = newCtx();
        ctx.setVariable("k", "v");
        assertThat(ctx.getVariable("k")).isEqualTo("v");
        assertThat(ctx.getVariable("missing")).isNull();
        assertThat(ctx.variables()).containsEntry("k", "v");
    }

    @Test
    void storesNodeMessagesImmutablyByCopy() {
        ExecutionContext ctx = newCtx();
        Map<Integer, String> src = Fixtures.fields(11, "ORD1");
        ctx.storeNodeMessage("n1", src);
        src.put(99, "mutated");
        assertThat(ctx.getNodeMessage("n1")).containsExactlyInAnyOrderEntriesOf(Map.of(11, "ORD1"));
        assertThat(ctx.getNodeMessage("absent")).isNull();
    }

    @Test
    void currentNodeIdAndStatusAreMutable() {
        ExecutionContext ctx = newCtx();
        ctx.setCurrentNodeId("abc");
        ctx.setStatus(ExecutionStatus.FAILED);
        assertThat(ctx.currentNodeId()).isEqualTo("abc");
        assertThat(ctx.status()).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    void nodeEventEmitterReceivesEvents() {
        ExecutionContext ctx = newCtx();
        AtomicReference<String> seen = new AtomicReference<>();
        ctx.setNodeEventEmitter((type, nodeId) -> seen.set(type + ":" + nodeId));
        ctx.emitNodeEvent(ExecutionEventType.NODE_ENTERED, "n1");
        assertThat(seen.get()).isEqualTo("NODE_ENTERED:n1");
    }

    @Test
    void defaultEmitterIsNoOp() {
        ExecutionContext ctx = newCtx();
        ctx.emitNodeEvent(ExecutionEventType.ERROR, "x"); // must not throw
    }

    @Test
    void stepListenerDefaultsToNoopAndNullResets() {
        ExecutionContext ctx = newCtx();
        assertThat(ctx.stepListener()).isEqualTo(StepListener.NOOP);
        AtomicReference<String> entered = new AtomicReference<>();
        ctx.setStepListener(new StepListener() {
            @Override public void entered(ScenarioNode node, ExecutionContext c) { entered.set(node.id()); }
            @Override public void completed(ScenarioNode node, NodeHandlerResult result,
                                            Instant start, Instant end, ExecutionContext c) {}
        });
        ScenarioNode node = node("n1", NodeType.SEND_FIX).build();
        ctx.stepListener().entered(node, ctx);
        assertThat(entered.get()).isEqualTo("n1");
        ctx.setStepListener(null);
        assertThat(ctx.stepListener()).isEqualTo(StepListener.NOOP);
    }
}
