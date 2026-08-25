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
    void quotedNumericGroupTagIsAcceptedLikeANumber() {
        ExecutionContext ctx = ctx();
        ctx.storeNodeMessage("src", Fixtures.fields(35, "8"));
        ScenarioNode v = node("v", NodeType.VALIDATE).cfg("sourceNodeId", "src")
                .cfg("rules", List.of(Map.of("tag", 600, "rule", "FIELD_PRESENT", "groupTag", "555")))
                .onSuccess("ok").onFailure("no").build();
        NodeHandlerResult r = handler.handle(v, ctx);
        // group 555 has no entries on this message, so the rule fails cleanly rather than throwing
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("no");
    }

    @Test
    void nonNumericGroupTagFailsCleanlyInsteadOfThrowing() {
        ExecutionContext ctx = ctx();
        ctx.storeNodeMessage("src", Fixtures.fields(35, "8"));
        ScenarioNode v = node("v", NodeType.VALIDATE).cfg("sourceNodeId", "src")
                .cfg("rules", List.of(Map.of("tag", 600, "rule", "FIELD_PRESENT", "groupTag", "not-a-number")))
                .onSuccess("ok").onFailure("no").build();
        NodeHandlerResult r = handler.handle(v, ctx);
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("no");
        assertThat(r.errorMessage()).contains("groupTag");
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

    // ---- issue #77: VALIDATE authored in the graphical editor ----

    @Test
    void withoutSourceNodeIdValidatesTheRunsLastInboundMessage() {
        // The editor writes no sourceNodeId, so the handler used to look the message up under the
        // VALIDATE node's own id and validate an empty message.
        ExecutionContext ctx = ctx();
        ctx.storeInboundMessage("expect", Fixtures.fields(35, "D", 55, "EUR/USD", 1, "ACC-001"));
        ScenarioNode v = node("v", NodeType.VALIDATE)
                .cfg("rules", List.of(
                        Map.of("tag", 55, "rule", "FIELD_PRESENT"),
                        Map.of("tag", 1, "rule", "FIELD_PRESENT")))
                .onSuccess("ok").onFailure("no").build();
        NodeHandlerResult r = handler.handle(v, ctx);
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("ok");
    }

    @Test
    void anExplicitSourceNodeIdStillWins() {
        ExecutionContext ctx = ctx();
        ctx.storeInboundMessage("later", Fixtures.fields(35, "8"));
        ctx.storeNodeMessage("src", Fixtures.fields(35, "D"));
        ScenarioNode v = node("v", NodeType.VALIDATE).cfg("sourceNodeId", "src")
                .cfg("rules", List.of(Map.of("tag", 35, "rule", "EQUALS", "value", "D")))
                .onSuccess("ok").onFailure("no").build();
        assertThat(handler.handle(v, ctx).success()).isTrue();
    }

    @Test
    void dateRulesWrittenAsAListDoNotBlowUp() {
        // The editor writes dateRules as a list; the handler used to cast it straight to a Map,
        // so `dateRules: []` threw ClassCastException before any rule ran.
        ExecutionContext ctx = ctx();
        ctx.storeInboundMessage("expect", Fixtures.fields(35, "D"));
        ScenarioNode v = node("v", NodeType.VALIDATE)
                .cfg("rules", List.of(Map.of("tag", 35, "rule", "EQUALS", "value", "D")))
                .cfg("dateRules", List.of())
                .onSuccess("ok").onFailure("no").build();
        NodeHandlerResult r = handler.handle(v, ctx);
        assertThat(r.success()).isTrue();
    }

    @Test
    void aDateRuleDefinedAsAListEntryIsResolvedById() {
        ExecutionContext ctx = ctx();
        ctx.storeInboundMessage("expect", Fixtures.fields(60, java.time.Instant.now().toString()));
        ScenarioNode v = node("v", NodeType.VALIDATE)
                .cfg("rules", List.of(Map.of("tag", 60, "rule", "DATE_RULE", "dateRule", "dr-1")))
                .cfg("dateRules", List.of(Map.of(
                        "ruleId", "dr-1", "type", "CURRENT_TIMESTAMP",
                        "toleranceValue", 5, "toleranceUnit", "SECONDS")))
                .onSuccess("ok").onFailure("no").build();
        NodeHandlerResult r = handler.handle(v, ctx);
        assertThat(r.success()).isTrue();
    }
}
