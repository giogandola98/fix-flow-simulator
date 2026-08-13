package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.RuntimePolicy;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.engine.handlers.EndHandler;
import com.fixflow.engine.handlers.NodeDispatcher;
import com.fixflow.engine.handlers.NodeHandlerResult;
import com.fixflow.engine.handlers.StartHandler;
import com.fixflow.engine.support.Fixtures;
import com.fixflow.engine.support.ProgrammableHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.fixflow.engine.support.Fixtures.node;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioExecutorTest {

    private NodeDispatcher dispatcher(ProgrammableHandler... extra) {
        List<com.fixflow.engine.handlers.NodeHandler> handlers = new java.util.ArrayList<>();
        handlers.add(new StartHandler());
        handlers.add(new EndHandler());
        handlers.add(new com.fixflow.engine.handlers.EndFailHandler());
        handlers.addAll(List.of(extra));
        return new NodeDispatcher(handlers);
    }

    @Test
    void passingScenarioReturnsPassed() throws InterruptedException {
        Scenario s = scenario("pass", start("end"), Fixtures.endPass("end"));
        ExecutionStatus status = new ScenarioExecutor(dispatcher()).execute(s, Fixtures.ctx(s));
        assertThat(status).isEqualTo(ExecutionStatus.PASSED);
    }

    @Test
    void endFailScenarioReturnsFailed() throws InterruptedException {
        Scenario s = scenario("fail", start("end"), Fixtures.endFail("end"));
        ExecutionStatus status = new ScenarioExecutor(dispatcher()).execute(s, Fixtures.ctx(s));
        assertThat(status).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    void nodeFailureWithNoBranchReturnsFailed() throws InterruptedException {
        ProgrammableHandler failing = new ProgrammableHandler(NodeType.SEND_FIX,
                (n, c) -> NodeHandlerResult.failure(null, "boom"));
        Scenario s = scenario("f", start("a"), node("a", NodeType.SEND_FIX).build());
        ExecutionStatus status = new ScenarioExecutor(dispatcher(failing)).execute(s, Fixtures.ctx(s));
        assertThat(status).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    void stoppedStatusIsPreserved() throws InterruptedException {
        ProgrammableHandler stopper = new ProgrammableHandler(NodeType.SEND_FIX, (n, c) -> {
            c.setStatus(ExecutionStatus.STOPPED);
            return NodeHandlerResult.terminal();
        });
        Scenario s = scenario("stop", start("a"), node("a", NodeType.SEND_FIX).build());
        ExecutionContext ctx = Fixtures.ctx(s);
        ExecutionStatus status = new ScenarioExecutor(dispatcher(stopper)).execute(s, ctx);
        assertThat(status).isEqualTo(ExecutionStatus.STOPPED);
    }

    @Test
    void missingStartNodeThrows() {
        Scenario noStart = new Scenario(UUID.randomUUID(), "nostart", "", "1", "s",
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(Fixtures.endPass("end")), List.of(), Map.of(), null);
        ScenarioExecutor exec = new ScenarioExecutor(dispatcher());
        assertThatThrownBy(() -> exec.execute(noStart, Fixtures.ctx(noStart)))
                .isInstanceOf(IllegalStateException.class);
    }
}
