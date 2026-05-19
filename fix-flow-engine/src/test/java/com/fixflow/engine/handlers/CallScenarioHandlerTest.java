package com.fixflow.engine.handlers;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.*;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.execution.ScenarioExecutor;
import com.fixflow.engine.execution.ScenarioExecutorPort;
import com.fixflow.engine.scenario.ScenarioRegistry;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CallScenarioHandlerTest {

    private ScenarioRegistry registry;
    private ScenarioExecutorPort executor;
    private CallScenarioHandler handler;

    private static Scenario scenario(UUID id, NodeType endType) {
        String endNode = endType == NodeType.END_PASS ? "n2pass" : "n2fail";
        return new Scenario(id, "sub", "", "1", "s",
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(
                        new ScenarioNode("n1", "start", NodeType.START, Map.of(), null, null, endNode, null, null),
                        new ScenarioNode(endNode, "end", endType, Map.of(), null, null, null, null, null)
                ),
                List.of(), Map.of(), null);
    }

    private static ExecutionContext ctx() {
        Scenario parent = new Scenario(UUID.randomUUID(), "parent", "", "1", "s",
                RuntimePolicy.PARALLEL, List.of(), List.of(), List.of(), List.of(), Map.of(), null);
        return new ExecutionContext(UUID.randomUUID(), parent, UUID.randomUUID());
    }

    @BeforeEach
    void setUp() {
        registry = new ScenarioRegistry();
        NodeDispatcher dispatcher = new NodeDispatcher(List.of(
                new StartHandler(), new EndHandler(), new EndFailHandler()));
        executor = new ScenarioExecutor(dispatcher);
        handler = new CallScenarioHandler(registry, executor, new VariableResolver());
    }

    @Test
    void childPassedReturnsSuccess() throws Exception {
        UUID childId = UUID.randomUUID();
        registry.register(scenario(childId, NodeType.END_PASS));
        ScenarioNode node = new ScenarioNode("cs", "call", NodeType.CALL_SCENARIO,
                Map.of("targetScenarioId", childId.toString()),
                null, null, "next", "fail", null);
        NodeHandlerResult r = handler.handle(node, ctx());
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("next");
    }

    @Test
    void childFailedReturnsFailure() throws Exception {
        UUID childId = UUID.randomUUID();
        registry.register(scenario(childId, NodeType.END_FAIL));
        ScenarioNode node = new ScenarioNode("cs", "call", NodeType.CALL_SCENARIO,
                Map.of("targetScenarioId", childId.toString()),
                null, null, "next", "fail", null);
        NodeHandlerResult r = handler.handle(node, ctx());
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("fail");
    }

    @Test
    void missingTargetReturnsFailure() throws Exception {
        ScenarioNode node = new ScenarioNode("cs", "call", NodeType.CALL_SCENARIO,
                Map.of(),
                null, null, "next", "fail", null);
        NodeHandlerResult r = handler.handle(node, ctx());
        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).contains("No target scenario configured");
    }

    @Test
    void unknownTargetIdReturnsFailure() throws Exception {
        ScenarioNode node = new ScenarioNode("cs", "call", NodeType.CALL_SCENARIO,
                Map.of("targetScenarioId", UUID.randomUUID().toString()),
                null, null, "next", "fail", null);
        NodeHandlerResult r = handler.handle(node, ctx());
        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).contains("Scenario not found");
    }

    @Test
    void depthLimitExceededReturnsFailure() throws Exception {
        UUID childId = UUID.randomUUID();
        registry.register(scenario(childId, NodeType.END_PASS));
        ScenarioNode node = new ScenarioNode("cs", "call", NodeType.CALL_SCENARIO,
                Map.of("targetScenarioId", childId.toString()),
                null, null, "next", "fail", null);
        ExecutionContext c = ctx();
        c.setVariable("call:depth", "5");
        NodeHandlerResult r = handler.handle(node, c);
        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).contains("Max call depth exceeded");
    }

    @Test
    void inputVarCopiedToChild() throws Exception {
        UUID childId = UUID.randomUUID();
        registry.register(scenario(childId, NodeType.END_PASS));
        ScenarioNode node = new ScenarioNode("cs", "call", NodeType.CALL_SCENARIO,
                Map.of("targetScenarioId", childId.toString(),
                       "inputVars", List.of(Map.of("from", "var:x", "to", "x"))),
                null, null, "next", "fail", null);
        ExecutionContext c = ctx();
        c.setVariable("x", "hello");
        NodeHandlerResult r = handler.handle(node, c);
        assertThat(r.success()).isTrue();
    }

    @Test
    void outputVarCopiedFromChild() throws Exception {
        UUID childId = UUID.randomUUID();
        NodeHandler setter = new NodeHandler() {
            @Override public NodeType getSupportedType() { return NodeType.VALIDATE; }
            @Override public NodeHandlerResult handle(ScenarioNode n, ExecutionContext ctx2) {
                ctx2.setVariable("result", "ok");
                return NodeHandlerResult.success(n.onSuccess());
            }
        };
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), setter, new EndHandler(), new EndFailHandler()));
        ScenarioExecutor ex = new ScenarioExecutor(d);
        CallScenarioHandler h = new CallScenarioHandler(registry, ex, new VariableResolver());

        Scenario child = new Scenario(childId, "sub", "", "1", "s",
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(
                        new ScenarioNode("n1", "start", NodeType.START,    Map.of(), null, null, "n2", null, null),
                        new ScenarioNode("n2", "set",   NodeType.VALIDATE, Map.of(), null, null, "n3", null, null),
                        new ScenarioNode("n3", "end",   NodeType.END_PASS, Map.of(), null, null, null, null, null)
                ),
                List.of(), Map.of(), null);
        registry.register(child);

        ScenarioNode node = new ScenarioNode("cs", "call", NodeType.CALL_SCENARIO,
                Map.of("targetScenarioId", childId.toString(),
                       "outputVars", List.of(Map.of("from", "result", "to", "parentResult"))),
                null, null, "next", "fail", null);
        ExecutionContext parent = ctx();
        NodeHandlerResult r = h.handle(node, parent);
        assertThat(r.success()).isTrue();
        assertThat(parent.getVariable("parentResult")).isEqualTo("ok");
    }
}
