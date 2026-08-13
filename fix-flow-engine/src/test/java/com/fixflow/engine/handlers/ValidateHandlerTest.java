package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.support.Fixtures;
import com.fixflow.engine.validation.DateRuleEngine;
import com.fixflow.engine.validation.ValidationEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.fixflow.engine.support.Fixtures.node;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static org.assertj.core.api.Assertions.assertThat;

class ValidateHandlerTest {

    private final ValidateHandler handler = new ValidateHandler(new ValidationEngine(new DateRuleEngine()));

    private ExecutionContext ctx() { return Fixtures.ctx(scenario("s", start("v"))); }

    @Test
    void supportsValidate() {
        assertThat(handler.getSupportedType()).isEqualTo(NodeType.VALIDATE);
    }

    @Test
    void passesWhenRuleMatchesSourceNodeMessage() {
        ExecutionContext ctx = ctx();
        ctx.storeNodeMessage("src", Fixtures.fields(35, "8"));
        ScenarioNode v = node("v", NodeType.VALIDATE).cfg("sourceNodeId", "src")
                .cfg("rules", List.of(Map.of("tag", 35, "rule", "EQUALS", "value", "8")))
                .onSuccess("ok").onFailure("no").build();
        NodeHandlerResult r = handler.handle(v, ctx);
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("ok");
    }

    @Test
    void failsWhenRuleMismatch() {
        ExecutionContext ctx = ctx();
        ctx.storeNodeMessage("src", Fixtures.fields(35, "D"));
        ScenarioNode v = node("v", NodeType.VALIDATE).cfg("sourceNodeId", "src")
                .cfg("rules", List.of(Map.of("tag", 35, "rule", "EQUALS", "value", "8")))
                .onSuccess("ok").onFailure("no").build();
        NodeHandlerResult r = handler.handle(v, ctx);
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("no");
        assertThat(r.errorMessage()).isEqualTo("validation failed");
    }

    @Test
    void missingSourceMessageValidatesAgainstEmptyFields() {
        ScenarioNode v = node("v", NodeType.VALIDATE)
                .cfg("rules", List.of(Map.of("tag", 35, "rule", "FIELD_PRESENT")))
                .onSuccess("ok").onFailure("no").build();
        NodeHandlerResult r = handler.handle(v, ctx());
        assertThat(r.success()).isFalse(); // no message -> field absent -> fail
    }

    @Test
    void acceptsLegacyValidationsKey() {
        ExecutionContext ctx = ctx();
        ctx.storeNodeMessage("v", Fixtures.fields(44, "10"));
        ScenarioNode v = node("v", NodeType.VALIDATE)
                .cfg("validations", List.of(Map.of("tag", 44, "rule", "NUMERIC_MIN", "numericValue", 5)))
                .onSuccess("ok").onFailure("no").build();
        assertThat(handler.handle(v, ctx).success()).isTrue();
    }

    @Test
    void strictModeRejectsUnexpectedField() {
        ExecutionContext ctx = ctx();
        ctx.storeNodeMessage("src", Fixtures.fields(35, "8", 44, "10"));
        ScenarioNode v = node("v", NodeType.VALIDATE).cfg("sourceNodeId", "src").cfg("strictMode", true)
                .cfg("rules", List.of(Map.of("tag", 35, "rule", "EQUALS", "value", "8")))
                .onSuccess("ok").onFailure("no").build();
        // tag 44 unexpected and non-header -> strict failure
        assertThat(handler.handle(v, ctx).success()).isFalse();
    }
}
