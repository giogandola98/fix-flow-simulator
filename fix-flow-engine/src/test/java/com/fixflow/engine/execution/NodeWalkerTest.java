package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.handlers.EndHandler;
import com.fixflow.engine.handlers.NodeDispatcher;
import com.fixflow.engine.handlers.NodeHandlerResult;
import com.fixflow.engine.handlers.StartHandler;
import com.fixflow.engine.support.Fixtures;
import com.fixflow.engine.support.ProgrammableHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.fixflow.engine.support.Fixtures.node;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static org.assertj.core.api.Assertions.assertThat;

class NodeWalkerTest {

    private final AtomicInteger dispatched = new AtomicInteger();

    private NodeWalker walkerWith(ProgrammableHandler handler) {
        return new NodeWalker(new NodeDispatcher(List.of(new StartHandler(), new EndHandler(), handler)));
    }

    @Test
    void runsOffEndOfGraphReturnsCompleted() throws InterruptedException {
        ProgrammableHandler h = new ProgrammableHandler(NodeType.SEND_FIX, (n, c) -> {
            dispatched.incrementAndGet();
            return NodeHandlerResult.success(n.onSuccess()); // onSuccess null -> off the end
        });
        Scenario s = scenario("s", start("a"), node("a", NodeType.SEND_FIX).build());
        ExecutionContext ctx = Fixtures.ctx(s);
        WalkOutcome outcome = walkerWith(h).walk(s.startNode().get(), ctx, null);
        assertThat(outcome).isEqualTo(WalkOutcome.COMPLETED);
        assertThat(dispatched).hasValue(1);
    }

    @Test
    void failureWithNoBranchReturnsFailed() throws InterruptedException {
        ProgrammableHandler h = new ProgrammableHandler(NodeType.SEND_FIX,
                (n, c) -> NodeHandlerResult.failure(null, "boom"));
        Scenario s = scenario("s", start("a"), node("a", NodeType.SEND_FIX).build());
        WalkOutcome outcome = walkerWith(h).walk(s.startNode().get(), Fixtures.ctx(s), null);
        assertThat(outcome).isEqualTo(WalkOutcome.FAILED);
    }

    @Test
    void reachingBoundaryReturnsBoundary() throws InterruptedException {
        // a -> loopNode (the boundary): walker hands back before re-entering it
        ProgrammableHandler h = new ProgrammableHandler(NodeType.SEND_FIX,
                (n, c) -> NodeHandlerResult.success("loopNode"));
        Scenario s = scenario("s",
                start("a"),
                node("a", NodeType.SEND_FIX).onSuccess("loopNode").build(),
                node("loopNode", NodeType.LOOP).build());
        WalkOutcome outcome = walkerWith(h).walk(s.findNode("a").get(), Fixtures.ctx(s), "loopNode");
        assertThat(outcome).isEqualTo(WalkOutcome.BOUNDARY);
    }

    @Test
    void terminalStatusMidWalkReturnsHalted() throws InterruptedException {
        // END node flips status to a terminal value during dispatch
        Scenario s = scenario("s", start("end"), Fixtures.endPass("end"));
        NodeWalker walker = new NodeWalker(new NodeDispatcher(List.of(new StartHandler(), new EndHandler())));
        ExecutionContext ctx = Fixtures.ctx(s);
        WalkOutcome outcome = walker.walk(s.startNode().get(), ctx, null);
        assertThat(outcome).isEqualTo(WalkOutcome.HALTED);
        assertThat(ctx.status()).isEqualTo(ExecutionStatus.PASSED);
    }

    @Test
    void statusNotRunningBeforeDispatchStopsImmediately() throws InterruptedException {
        ProgrammableHandler h = new ProgrammableHandler(NodeType.SEND_FIX, (n, c) -> {
            dispatched.incrementAndGet();
            return NodeHandlerResult.success(n.onSuccess());
        });
        Scenario s = scenario("s", start("a"), node("a", NodeType.SEND_FIX).build());
        ExecutionContext ctx = Fixtures.ctx(s);
        ctx.setStatus(ExecutionStatus.STOPPED); // cooperative pre-check
        WalkOutcome outcome = walkerWith(h).walk(s.startNode().get(), ctx, null);
        assertThat(outcome).isEqualTo(WalkOutcome.HALTED);
        assertThat(dispatched).hasValue(0);
    }

    @Test
    void nullStartReturnsCompleted() throws InterruptedException {
        Scenario s = scenario("s", start("a"), node("a", NodeType.SEND_FIX).build());
        NodeWalker walker = new NodeWalker(new NodeDispatcher(List.of(new StartHandler())));
        assertThat(walker.walk(null, Fixtures.ctx(s), null)).isEqualTo(WalkOutcome.COMPLETED);
    }

    @Test
    void updatesCurrentNodeIdAsItWalks() throws InterruptedException {
        ProgrammableHandler h = new ProgrammableHandler(NodeType.SEND_FIX,
                (n, c) -> NodeHandlerResult.success(n.onSuccess()));
        Scenario s = scenario("s", start("a"), node("a", NodeType.SEND_FIX).build());
        ExecutionContext ctx = Fixtures.ctx(s);
        walkerWith(h).walk(s.startNode().get(), ctx, null);
        assertThat(ctx.currentNodeId()).isEqualTo("a");
    }
}
