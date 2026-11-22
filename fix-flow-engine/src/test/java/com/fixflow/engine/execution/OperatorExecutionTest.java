package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.engine.handlers.EndHandler;
import com.fixflow.engine.handlers.NodeDispatcher;
import com.fixflow.engine.handlers.OperatorHandler;
import com.fixflow.engine.handlers.StartHandler;
import com.fixflow.engine.operator.OperatorGateway;
import com.fixflow.engine.scenario.ScenarioRegistry;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.fixflow.engine.support.Fixtures.endPass;
import static com.fixflow.engine.support.Fixtures.node;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OperatorExecutionTest {

    private final ScenarioRegistry registry = new ScenarioRegistry();
    private final OperatorGateway gateway = new OperatorGateway();

    private ExecutionManager manager() {
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndHandler(),
                new OperatorHandler(gateway, new VariableResolver())));
        ExecutionManager mgr = new ExecutionManager(registry, d);
        mgr.setOperatorGateway(gateway);
        return mgr;
    }

    private Scenario scenarioWithOperator() {
        Scenario s = scenario("op", start("op"),
                node("op", NodeType.OPERATOR).cfg("instruction", "go").onSuccess("end").onFailure("end").build(),
                endPass("end"));
        registry.register(s);
        return s;
    }

    @Test
    void stop_cancels_the_open_prompt_and_lands_stopped() {
        ExecutionManager mgr = manager();
        UUID id = mgr.start(scenarioWithOperator().id(), UUID.randomUUID());
        await().atMost(Duration.ofSeconds(5)).until(() -> gateway.open(id).isPresent());
        mgr.stop(id);
        await().atMost(Duration.ofSeconds(5)).until(() -> mgr.getContext(id) == null);
        assertThat(mgr.getStatus(id)).isEqualTo(ExecutionStatus.STOPPED);
        assertThat(gateway.open(id)).isEmpty();
    }

    @Test
    void answering_lets_the_run_finish() {
        ExecutionManager mgr = manager();
        UUID id = mgr.start(scenarioWithOperator().id(), UUID.randomUUID());
        await().atMost(Duration.ofSeconds(5)).until(() -> gateway.open(id).isPresent());
        assertThat(gateway.answer(id, "op", true, "")).isTrue();
        await().atMost(Duration.ofSeconds(5)).until(() -> mgr.getStatus(id) == ExecutionStatus.PASSED);
    }
}
