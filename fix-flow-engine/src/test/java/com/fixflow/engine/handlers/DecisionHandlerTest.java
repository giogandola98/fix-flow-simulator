package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.support.Fixtures;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import static com.fixflow.engine.support.Fixtures.node;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisionHandlerTest {

    private final DecisionHandler handler = new DecisionHandler(new VariableResolver());

    private ExecutionContext ctx() {
        Scenario s = scenario("s", start("d"), node("d", NodeType.DECISION).onSuccess("ok").onFailure("no").build());
        return Fixtures.ctx(s);
    }

    private NodeHandlerResult run(String condition) {
        return handler.handle(node("d", NodeType.DECISION).cfg("condition", condition)
                .onSuccess("ok").onFailure("no").build(), ctx());
    }

    @Test
    void supportsDecision() {
        assertThat(handler.getSupportedType()).isEqualTo(NodeType.DECISION);
    }

    @Test
    void equalsTrueRoutesOnSuccess() {
        NodeHandlerResult r = run("foo == foo");
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("ok");
    }

    @Test
    void equalsFalseRoutesOnFailure() {
        NodeHandlerResult r = run("foo == bar");
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("no");
        assertThat(r.errorMessage()).isEqualTo("condition false");
    }

    @Test
    void notEqualsOperator() {
        assertThat(run("foo != bar").success()).isTrue();
        assertThat(run("foo != foo").success()).isFalse();
    }

    @Test
    void containsOperator() {
        assertThat(run("hello world contains world").success()).isTrue();
        assertThat(run("hello contains zzz").success()).isFalse();
    }

    @Test
    void unquotesQuotedOperands() {
        assertThat(run("\"a b\" == \"a b\"").success()).isTrue();
    }

    @Test
    void resolvesVariablesBeforeEvaluating() {
        ExecutionContext ctx = ctx();
        ctx.setVariable("side", "BUY");
        NodeHandlerResult r = handler.handle(node("d", NodeType.DECISION)
                .cfg("condition", "{{var:side}} == BUY").onSuccess("ok").onFailure("no").build(), ctx);
        assertThat(r.success()).isTrue();
    }

    @Test
    void missingConditionRoutesOnFailure() {
        NodeHandlerResult r = handler.handle(node("d", NodeType.DECISION)
                .onSuccess("ok").onFailure("no").build(), ctx());
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("no");
        assertThat(r.errorMessage()).isEqualTo("missing condition");
    }

    @Test
    void unparseableConditionThrows() {
        assertThatThrownBy(() -> run("this has no operator"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
