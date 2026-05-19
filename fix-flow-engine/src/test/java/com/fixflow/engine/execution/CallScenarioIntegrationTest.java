package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.*;
import com.fixflow.engine.handlers.*;
import com.fixflow.engine.scenario.ScenarioRegistry;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class CallScenarioIntegrationTest {

    private ScenarioRegistry registry;
    private ExecutionManager manager;

    @BeforeEach
    void setUp() {
        registry = new ScenarioRegistry();
        VariableResolver resolver = new VariableResolver();
        ScenarioExecutorHolder holder = new ScenarioExecutorHolder();
        NodeDispatcher dispatcher = new NodeDispatcher(List.of(
                new StartHandler(),
                new EndHandler(),
                new EndFailHandler(),
                new CallScenarioHandler(registry, holder, resolver)
        ));
        holder.setExecutor(new ScenarioExecutor(dispatcher));
        manager = new ExecutionManager(registry, dispatcher);
    }

    static class ScenarioExecutorHolder implements ScenarioExecutorPort {
        private ScenarioExecutor executor;
        void setExecutor(ScenarioExecutor e) { this.executor = e; }
        @Override
        public ExecutionStatus execute(Scenario scenario, ExecutionContext ctx) throws InterruptedException {
            return executor.execute(scenario, ctx);
        }
    }

    private UUID registerSimpleScenario(String name, NodeType endType) {
        UUID id = UUID.randomUUID();
        String endId = endType == NodeType.END_PASS ? "end-pass" : "end-fail";
        registry.register(new Scenario(id, name, "", "1", null,
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(
                        new ScenarioNode("s", "start", NodeType.START, Map.of(), null, null, endId, null, null),
                        new ScenarioNode(endId, "end", endType, Map.of(), null, null, null, null, null)
                ),
                List.of(), Map.of(), null));
        return id;
    }

    @Test
    void parentCallsChildThatPasses() {
        UUID childId = registerSimpleScenario("child", NodeType.END_PASS);
        UUID parentId = UUID.randomUUID();
        registry.register(new Scenario(parentId, "parent", "", "1", null,
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(
                        new ScenarioNode("s",  "start", NodeType.START, Map.of(), null, null, "cs", null, null),
                        new ScenarioNode("cs", "call",  NodeType.CALL_SCENARIO,
                                Map.of("targetScenarioId", childId.toString()),
                                null, null, "end", "fail", null),
                        new ScenarioNode("end", "end", NodeType.END_PASS, Map.of(), null, null, null, null, null)
                ),
                List.of(), Map.of(), null));

        UUID execId = manager.start(parentId, null);
        await().atMost(3, TimeUnit.SECONDS)
               .until(() -> manager.getStatus(execId) != ExecutionStatus.RUNNING);
        assertThat(manager.getStatus(execId)).isEqualTo(ExecutionStatus.PASSED);
    }

    @Test
    void parentCallsChildThatFails() {
        UUID childId = registerSimpleScenario("child", NodeType.END_FAIL);
        UUID parentId = UUID.randomUUID();
        registry.register(new Scenario(parentId, "parent", "", "1", null,
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(
                        new ScenarioNode("s",   "start", NodeType.START, Map.of(), null, null, "cs", null, null),
                        new ScenarioNode("cs",  "call",  NodeType.CALL_SCENARIO,
                                Map.of("targetScenarioId", childId.toString()),
                                null, null, "end-pass", "end-fail", null),
                        new ScenarioNode("end-pass", "pass", NodeType.END_PASS, Map.of(), null, null, null, null, null),
                        new ScenarioNode("end-fail", "fail", NodeType.END_FAIL, Map.of(), null, null, null, null, null)
                ),
                List.of(), Map.of(), null));

        UUID execId = manager.start(parentId, null);
        await().atMost(3, TimeUnit.SECONDS)
               .until(() -> manager.getStatus(execId) != ExecutionStatus.RUNNING);
        assertThat(manager.getStatus(execId)).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    void varRoundTrip() {
        UUID childId = UUID.randomUUID();
        NodeHandler setter = new NodeHandler() {
            @Override public NodeType getSupportedType() { return NodeType.VALIDATE; }
            @Override public NodeHandlerResult handle(ScenarioNode n, ExecutionContext ctx2) {
                ctx2.setVariable("childOutput", "done");
                return NodeHandlerResult.success(n.onSuccess());
            }
        };
        NodeDispatcher d2 = new NodeDispatcher(List.of(new StartHandler(), setter, new EndHandler(), new EndFailHandler()));
        Scenario child = new Scenario(childId, "child", "", "1", null,
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(
                        new ScenarioNode("s", "start",  NodeType.START,    Map.of(), null, null, "v", null, null),
                        new ScenarioNode("v", "setter", NodeType.VALIDATE, Map.of(), null, null, "e", null, null),
                        new ScenarioNode("e", "end",    NodeType.END_PASS, Map.of(), null, null, null, null, null)
                ),
                List.of(), Map.of(), null);
        registry.register(child);

        ScenarioExecutorHolder holder2 = new ScenarioExecutorHolder();
        NodeDispatcher dispatcher2 = new NodeDispatcher(List.of(
                new StartHandler(), new EndHandler(), new EndFailHandler(),
                new CallScenarioHandler(registry, holder2, new VariableResolver())
        ));
        holder2.setExecutor(new ScenarioExecutor(d2));
        ExecutionManager mgr2 = new ExecutionManager(registry, dispatcher2);

        UUID parentId = UUID.randomUUID();
        registry.register(new Scenario(parentId, "parent", "", "1", null,
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(
                        new ScenarioNode("s", "start", NodeType.START, Map.of(), null, null, "cs", null, null),
                        new ScenarioNode("cs", "call", NodeType.CALL_SCENARIO,
                                Map.of("targetScenarioId", childId.toString(),
                                       "outputVars", List.of(Map.of("from", "childOutput", "to", "parentResult"))),
                                null, null, "end", "fail", null),
                        new ScenarioNode("end", "end", NodeType.END_PASS, Map.of(), null, null, null, null, null)
                ),
                List.of(), Map.of(), null));

        UUID execId = mgr2.start(parentId, null);
        await().atMost(3, TimeUnit.SECONDS)
               .until(() -> mgr2.getStatus(execId) != ExecutionStatus.RUNNING);
        assertThat(mgr2.getStatus(execId)).isEqualTo(ExecutionStatus.PASSED);
    }

    @Test
    void recursionFailsAtDepthLimit() {
        UUID aId = UUID.randomUUID();
        registry.register(new Scenario(aId, "recursive", "", "1", null,
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(
                        new ScenarioNode("s",    "start", NodeType.START,         Map.of(), null, null, "cs", null, null),
                        new ScenarioNode("cs",   "call",  NodeType.CALL_SCENARIO,
                                Map.of("targetScenarioId", aId.toString()),
                                null, null, "end", "fail", null),
                        new ScenarioNode("end",  "pass",  NodeType.END_PASS,  Map.of(), null, null, null, null, null),
                        new ScenarioNode("fail", "fail",  NodeType.END_FAIL,  Map.of(), null, null, null, null, null)
                ),
                List.of(), Map.of(), null));

        UUID execId = manager.start(aId, null);
        await().atMost(5, TimeUnit.SECONDS)
               .until(() -> manager.getStatus(execId) != ExecutionStatus.RUNNING);
        assertThat(manager.getStatus(execId)).isEqualTo(ExecutionStatus.FAILED);
    }
}
