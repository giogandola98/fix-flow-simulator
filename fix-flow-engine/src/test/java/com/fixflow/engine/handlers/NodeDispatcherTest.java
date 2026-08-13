package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.support.Fixtures;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.fixflow.engine.support.Fixtures.node;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static org.assertj.core.api.Assertions.assertThat;

class NodeDispatcherTest {

    private ExecutionContext ctx(Scenario s) { return Fixtures.ctx(s); }

    @Test
    void branchAliasRoutesToDecisionHandler() throws Exception {
        NodeDispatcher d = new NodeDispatcher(List.of(new DecisionHandler(new VariableResolver()), new WaitHandler()));
        Scenario s = scenario("s", start("b"),
                node("b", NodeType.BRANCH).cfg("condition", "x == x").onSuccess("ok").onFailure("no").build());
        NodeHandlerResult r = d.dispatch(s.findNode("b").get(), ctx(s));
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("ok");
    }

    @Test
    void timeoutAliasRoutesToWaitHandler() throws Exception {
        NodeDispatcher d = new NodeDispatcher(List.of(new DecisionHandler(new VariableResolver()), new WaitHandler()));
        Scenario s = scenario("s", start("t"), node("t", NodeType.TIMEOUT).onSuccess("next").build());
        NodeHandlerResult r = d.dispatch(s.findNode("t").get(), ctx(s));
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("next");
    }

    @Test
    void unknownNodeTypeReturnsFailure() throws Exception {
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler()));
        Scenario s = scenario("s", start("x"), node("x", NodeType.HTTP_REQUEST).build());
        NodeHandlerResult r = d.dispatch(s.findNode("x").get(), ctx(s));
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isNull();
        assertThat(r.errorMessage()).contains("No handler for node type");
    }

    @Test
    void registeredHandlerIsInvoked() throws Exception {
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler()));
        Scenario s = scenario("s", start("go"));
        NodeHandlerResult r = d.dispatch(s.startNode().get(), ctx(s));
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("go");
    }
}
