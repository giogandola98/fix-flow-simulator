package com.fixflow.engine.handlers;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.execution.ScenarioExecutor;
import com.fixflow.engine.scenario.ScenarioRegistry;
import com.fixflow.engine.support.Fixtures;
import com.fixflow.engine.support.ProgrammableHandler;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.fixflow.engine.support.Fixtures.node;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static org.assertj.core.api.Assertions.assertThat;

class CallScenarioHandlerTest {

    private ScenarioRegistry registry;
    private CallScenarioHandler handler;

    @BeforeEach
    void setUp() {
        registry = new ScenarioRegistry();
        ProgrammableHandler stopper = new ProgrammableHandler(NodeType.WAIT, (n, c) -> {
            c.setStatus(ExecutionStatus.STOPPED);
            return NodeHandlerResult.terminal();
        });
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndHandler(), new EndFailHandler(), stopper));
        ScenarioExecutor executor = new ScenarioExecutor(d);
        handler = new CallScenarioHandler(registry, executor, new VariableResolver());
    }

    private ScenarioNode callNode(String targetId) {
        return node("call", NodeType.CALL_SCENARIO).cfg("targetScenarioId", targetId)
                .onSuccess("ok").onFailure("no").build();
    }

    private ExecutionContext parentCtx() { return Fixtures.ctx(scenario("parent", start("call"))); }

    @Test
    void supportsCallScenario() {
        assertThat(handler.getSupportedType()).isEqualTo(NodeType.CALL_SCENARIO);
    }

    @Test
    void subScenarioPassRoutesOnSuccess() throws Exception {
        Scenario target = scenario(UUID.randomUUID(), "child", List.of(), start("end"), Fixtures.endPass("end"));
        registry.register(target);
        NodeHandlerResult r = handler.handle(callNode(target.id().toString()), parentCtx());
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("ok");
    }

    @Test
    void subScenarioFailRoutesOnFailure() throws Exception {
        Scenario target = scenario(UUID.randomUUID(), "child", List.of(), start("end"), Fixtures.endFail("end"));
        registry.register(target);
        NodeHandlerResult r = handler.handle(callNode(target.id().toString()), parentCtx());
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("no");
        assertThat(r.errorMessage()).contains("Sub-scenario ended with FAIL");
    }

    @Test
    void subScenarioStoppedPropagatesToParent() throws Exception {
        Scenario target = scenario(UUID.randomUUID(), "child", List.of(),
                start("stop"), node("stop", NodeType.WAIT).build());
        registry.register(target);
        ExecutionContext parent = parentCtx();
        NodeHandlerResult r = handler.handle(callNode(target.id().toString()), parent);
        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).isEqualTo("Sub-scenario was stopped");
        assertThat(parent.status()).isEqualTo(ExecutionStatus.STOPPED);
    }

    @Test
    void passesInputAndOutputVariables() throws Exception {
        Scenario target = scenario(UUID.randomUUID(), "child", List.of(), start("end"), Fixtures.endPass("end"));
        registry.register(target);
        ScenarioNode call = node("call", NodeType.CALL_SCENARIO)
                .cfg("targetScenarioId", target.id().toString())
                .cfg("inputVars", List.of(Map.of("from", "var:x", "to", "y")))
                .cfg("outputVars", List.of(Map.of("from", "y", "to", "z")))
                .onSuccess("ok").onFailure("no").build();
        ExecutionContext parent = parentCtx();
        parent.setVariable("x", "hello");
        NodeHandlerResult r = handler.handle(call, parent);
        assertThat(r.success()).isTrue();
        assertThat(parent.getVariable("z")).isEqualTo("hello");
    }

    @Test
    void maxDepthExceededRoutesOnFailure() throws Exception {
        Scenario target = scenario(UUID.randomUUID(), "child", List.of(), start("end"), Fixtures.endPass("end"));
        registry.register(target);
        ExecutionContext parent = parentCtx();
        parent.setVariable("call:depth", "5"); // childDepth would be 6 > MAX_DEPTH 5
        NodeHandlerResult r = handler.handle(callNode(target.id().toString()), parent);
        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).contains("Max call depth exceeded");
    }

    @Test
    void blankTargetRoutesOnFailure() throws Exception {
        NodeHandlerResult r = handler.handle(callNode(""), parentCtx());
        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).isEqualTo("No target scenario configured");
    }

    @Test
    void invalidUuidRoutesOnFailure() throws Exception {
        NodeHandlerResult r = handler.handle(callNode("not-a-uuid"), parentCtx());
        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).contains("Invalid targetScenarioId");
    }

    @Test
    void unknownScenarioRoutesOnFailure() throws Exception {
        NodeHandlerResult r = handler.handle(callNode(UUID.randomUUID().toString()), parentCtx());
        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).contains("Scenario not found");
    }
}
