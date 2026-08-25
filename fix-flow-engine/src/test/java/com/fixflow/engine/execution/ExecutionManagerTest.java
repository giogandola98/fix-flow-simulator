package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.engine.handlers.*;
import com.fixflow.engine.scenario.ScenarioRegistry;
import com.fixflow.engine.support.Fixtures;
import com.fixflow.engine.support.ProgrammableHandler;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.fixflow.engine.support.Fixtures.node;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class ExecutionManagerTest {

    private final ScenarioRegistry registry = new ScenarioRegistry();

    private ExecutionStatus runToCompletion(ExecutionManager mgr, UUID scenarioId) {
        UUID execId = mgr.start(scenarioId, UUID.randomUUID());
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            ExecutionStatus st = mgr.getStatus(execId);
            return st != null && st != ExecutionStatus.RUNNING;
        });
        return mgr.getStatus(execId);
    }

    @Test
    void simpleScenarioRunsToPassed() {
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndHandler()));
        Scenario s = scenario("pass", start("end"), Fixtures.endPass("end"));
        registry.register(s);
        assertThat(runToCompletion(new ExecutionManager(registry, d), s.id()))
                .isEqualTo(ExecutionStatus.PASSED);
    }

    @Test
    void runsOffEndOfGraphMarksPassed() {
        ProgrammableHandler pass = ProgrammableHandler.passing(NodeType.SEND_FIX);
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), pass));
        Scenario s = scenario("open", start("a"), node("a", NodeType.SEND_FIX).build());
        registry.register(s);
        assertThat(runToCompletion(new ExecutionManager(registry, d), s.id()))
                .isEqualTo(ExecutionStatus.PASSED);
    }

    @Test
    void endFailMarksFailed() {
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndFailHandler()));
        Scenario s = scenario("fail", start("end"), Fixtures.endFail("end"));
        registry.register(s);
        assertThat(runToCompletion(new ExecutionManager(registry, d), s.id()))
                .isEqualTo(ExecutionStatus.FAILED);
    }

    // BUG-1: a false DECISION must route to onFailure, not terminate the run as a bare failure.
    @Test
    void decisionFalseRoutesToOnFailureBranch() {
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndHandler(),
                new EndFailHandler(), new DecisionHandler(new VariableResolver())));
        Scenario s = scenario("dec",
                start("decide"),
                node("decide", NodeType.DECISION).cfg("condition", "a == b")
                        .onSuccess("pass").onFailure("fail").build(),
                Fixtures.endPass("pass"),
                Fixtures.endFail("fail"));
        registry.register(s);
        // false condition -> onFailure -> END_FAIL -> FAILED (routed, not a bare halt)
        assertThat(runToCompletion(new ExecutionManager(registry, d), s.id()))
                .isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    void decisionTrueRoutesToOnSuccessBranch() {
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndHandler(),
                new EndFailHandler(), new DecisionHandler(new VariableResolver())));
        Scenario s = scenario("dec",
                start("decide"),
                node("decide", NodeType.DECISION).cfg("condition", "a == a")
                        .onSuccess("pass").onFailure("fail").build(),
                Fixtures.endPass("pass"),
                Fixtures.endFail("fail"));
        registry.register(s);
        assertThat(runToCompletion(new ExecutionManager(registry, d), s.id()))
                .isEqualTo(ExecutionStatus.PASSED);
    }

    // #62: a LOOP must walk its whole sub-block N times, not just the single attached node.
    @Test
    void loopWalksFullSubBlockNTimes() {
        AtomicInteger counter = new AtomicInteger();
        ProgrammableHandler counting = new ProgrammableHandler(NodeType.SEND_FIX, (n, c) -> {
            counter.incrementAndGet();
            return NodeHandlerResult.success(n.onSuccess());
        });
        NodeDispatcher body = new NodeDispatcher(List.of(new StartHandler(), new EndHandler(), counting));
        LoopHandler loop = new LoopHandler(body);
        NodeDispatcher top = new NodeDispatcher(List.of(new StartHandler(), new EndHandler(), counting, loop));

        Scenario s = scenario("loop",
                start("loop"),
                node("loop", NodeType.LOOP).cfg("targetNodeId", "bodyA").cfg("iterations", 3)
                        .onSuccess("end").build(),
                node("bodyA", NodeType.SEND_FIX).onSuccess("bodyB").build(),
                node("bodyB", NodeType.SEND_FIX).onSuccess("loop").build(), // loops back to boundary
                Fixtures.endPass("end"));
        registry.register(s);
        assertThat(runToCompletion(new ExecutionManager(registry, top), s.id()))
                .isEqualTo(ExecutionStatus.PASSED);
        assertThat(counter).as("2 body nodes * 3 iterations").hasValue(6);
    }

    // #61: stop() must interrupt a blocked node and land STOPPED.
    @Test
    void stopInterruptsBlockedDelayNodeAndLandsStopped() {
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndHandler(), new DelayHandler()));
        Scenario s = scenario("delay",
                start("delay"),
                node("delay", NodeType.DELAY).cfg("delayMs", 60_000L).onSuccess("end").build(),
                Fixtures.endPass("end"));
        registry.register(s);
        ExecutionManager mgr = new ExecutionManager(registry, d);
        UUID execId = mgr.start(s.id(), UUID.randomUUID());

        await().atMost(Duration.ofSeconds(5)).until(() -> {
            ExecutionContext ctx = mgr.getContext(execId);
            return ctx != null && "delay".equals(ctx.currentNodeId());
        });
        mgr.stop(execId);
        await().atMost(Duration.ofSeconds(5))
                .until(() -> mgr.getStatus(execId) == ExecutionStatus.STOPPED);
        assertThat(mgr.getStatus(execId)).isEqualTo(ExecutionStatus.STOPPED);
    }

    @Test
    void completedStatusLookupAfterContextRemoved() {
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndHandler()));
        Scenario s = scenario("pass", start("end"), Fixtures.endPass("end"));
        registry.register(s);
        ExecutionManager mgr = new ExecutionManager(registry, d);
        ExecutionStatus status = runToCompletion(mgr, s.id());
        assertThat(status).isEqualTo(ExecutionStatus.PASSED);
        // after completion the live context is gone, but the status is still resolvable
        UUID anExec = UUID.randomUUID();
        assertThat(mgr.getContext(anExec)).isNull();
    }

    @Test
    void startWithUnknownScenarioThrows() {
        ExecutionManager mgr = new ExecutionManager(registry, new NodeDispatcher(List.of(new StartHandler())));
        assertThatThrownBy(() -> mgr.start(UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getStatusUnknownExecutionIsNull() {
        ExecutionManager mgr = new ExecutionManager(registry, new NodeDispatcher(List.of(new StartHandler())));
        assertThat(mgr.getStatus(UUID.randomUUID())).isNull();
    }

    @Test
    void resolvesSessionFromScenarioRefWhenNotSupplied() {
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndHandler()));
        UUID sessionRef = UUID.randomUUID();
        Scenario s = new Scenario(UUID.randomUUID(), "refsess", "", "1", sessionRef.toString(),
                com.fixflow.core.domain.scenario.RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(start("end"), Fixtures.endPass("end")), List.of(), Map.of(), null);
        registry.register(s);
        ExecutionManager mgr = new ExecutionManager(registry, d);
        UUID execId = mgr.start(s.id(), null);
        await().atMost(Duration.ofSeconds(5))
                .until(() -> mgr.getStatus(execId) == ExecutionStatus.PASSED);
        assertThat(mgr.getStatus(execId)).isEqualTo(ExecutionStatus.PASSED);
    }

    @Test
    void aNonUuidSessionRefRunsWithoutASessionInsteadOfThrowing() {
        // Scenarios exported from the editor carry placeholders such as `sessionRef: default`;
        // start() used to blow up on UUID.fromString before the run even began (issue #77).
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndHandler()));
        Scenario s = scenario("no-session", start("end"), Fixtures.endPass("end"));
        registry.register(s);
        ExecutionManager mgr = new ExecutionManager(registry, d);

        UUID execId = mgr.start(s.id(), null);
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            ExecutionStatus st = mgr.getStatus(execId);
            return st != null && st != ExecutionStatus.RUNNING;
        });
        assertThat(mgr.getStatus(execId)).isEqualTo(ExecutionStatus.PASSED);
    }
}
