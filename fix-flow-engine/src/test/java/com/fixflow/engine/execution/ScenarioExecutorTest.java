package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.*;
import com.fixflow.engine.handlers.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioExecutorTest {

    private static Scenario passingScenario(UUID id) {
        return new Scenario(id, "pass", "", "1", "s",
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(
                        new ScenarioNode("n1", "start", NodeType.START,    Map.of(), null, null, "n2", null, null),
                        new ScenarioNode("n2", "end",   NodeType.END_PASS,  Map.of(), null, null, null, null, null)
                ),
                List.of(), Map.of(), null);
    }

    private static Scenario failingScenario(UUID id) {
        return new Scenario(id, "fail", "", "1", "s",
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(
                        new ScenarioNode("n1", "start", NodeType.START,    Map.of(), null, null, "n2", null, null),
                        new ScenarioNode("n2", "end",   NodeType.END_FAIL,  Map.of(), null, null, null, null, null)
                ),
                List.of(), Map.of(), null);
    }

    private static NodeDispatcher dispatcher() {
        return new NodeDispatcher(List.of(
                new StartHandler(),
                new EndHandler(),
                new EndFailHandler()
        ));
    }

    private static ExecutionContext ctx(Scenario s) {
        return new ExecutionContext(UUID.randomUUID(), s, UUID.randomUUID());
    }

    @Test
    void passingScenarioReturnsPassed() throws InterruptedException {
        Scenario s = passingScenario(UUID.randomUUID());
        ScenarioExecutor exec = new ScenarioExecutor(dispatcher());
        ExecutionStatus status = exec.execute(s, ctx(s));
        assertThat(status).isEqualTo(ExecutionStatus.PASSED);
    }

    @Test
    void failingScenarioReturnsFailed() throws InterruptedException {
        Scenario s = failingScenario(UUID.randomUUID());
        ScenarioExecutor exec = new ScenarioExecutor(dispatcher());
        ExecutionStatus status = exec.execute(s, ctx(s));
        assertThat(status).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    void stoppedContextHaltsImmediately() throws InterruptedException {
        Scenario s = passingScenario(UUID.randomUUID());
        ScenarioExecutor exec = new ScenarioExecutor(dispatcher());
        ExecutionContext c = ctx(s);
        c.setStatus(ExecutionStatus.STOPPED);
        ExecutionStatus status = exec.execute(s, c);
        assertThat(status).isEqualTo(ExecutionStatus.STOPPED);
    }
}
