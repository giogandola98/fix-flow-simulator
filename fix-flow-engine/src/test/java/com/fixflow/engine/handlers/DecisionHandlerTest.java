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

    // ---- issue #86: several conditions, several routes ----

    private java.util.Map<String, Object> branch(String id, String label, java.util.List<String> conditions, String target) {
        java.util.Map<String, Object> b = new java.util.LinkedHashMap<>();
        b.put("branchId", id);
        b.put("label", label);
        b.put("conditions", conditions);
        b.put("targetNodeId", target);
        return b;
    }

    private NodeHandlerResult runBranches(ExecutionContext ctx, java.util.List<java.util.Map<String, Object>> branches) {
        return handler.handle(node("d", NodeType.DECISION).cfg("branches", branches)
                .onSuccess("ok").onFailure("no").build(), ctx);
    }

    @Test
    void firstMatchingBranchWins() {
        NodeHandlerResult r = runBranches(ctx(), java.util.List.of(
                branch("b1", "Filled", java.util.List.of("2 == 1"), "n-filled"),
                branch("b2", "Partial", java.util.List.of("1 == 1"), "n-partial"),
                branch("b3", "Also true", java.util.List.of("1 == 1"), "n-never")));
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("n-partial");
    }

    @Test
    void everyConditionOfABranchMustHold() {
        NodeHandlerResult r = runBranches(ctx(), java.util.List.of(
                branch("b1", "Both", java.util.List.of("1 == 1", "2 == 3"), "n-both"),
                branch("b2", "Second", java.util.List.of("1 == 1", "2 == 2"), "n-second")));
        assertThat(r.nextNodeId()).isEqualTo("n-second");
    }

    @Test
    void aBranchWithoutConditionsIsTheDefault() {
        NodeHandlerResult r = runBranches(ctx(), java.util.List.of(
                branch("b1", "Filled", java.util.List.of("1 == 2"), "n-filled"),
                branch("b2", "Anything else", java.util.List.of(), "n-default")));
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("n-default");
    }

    @Test
    void theDefaultIsTakenOnlyWhenNothingElseMatches() {
        NodeHandlerResult r = runBranches(ctx(), java.util.List.of(
                branch("b0", "Anything else", java.util.List.of(), "n-default"),
                branch("b1", "Filled", java.util.List.of("1 == 1"), "n-filled")));
        assertThat(r.nextNodeId()).isEqualTo("n-filled");
    }

    @Test
    void noBranchAndNoDefaultFailsToOnFailure() {
        NodeHandlerResult r = runBranches(ctx(), java.util.List.of(
                branch("b1", "Filled", java.util.List.of("1 == 2"), "n-filled")));
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("no");
        assertThat(r.errorMessage()).isEqualTo("no branch matched");
    }

    @Test
    void aBranchWithoutATargetFallsBackToOnSuccess() {
        NodeHandlerResult r = runBranches(ctx(), java.util.List.of(
                branch("b1", "Filled", java.util.List.of("1 == 1"), "")));
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("ok");
    }

    @Test
    void blankConditionRowsAreIgnoredRatherThanTurningTheBranchIntoADefault() {
        java.util.Map<String, Object> withBlank = branch("b1", "Filled",
                java.util.Arrays.asList("  ", "1 == 2"), "n-filled");
        NodeHandlerResult r = runBranches(ctx(), java.util.List.of(withBlank));
        // the only real condition is false, so this is not a match and not a default either
        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).isEqualTo("no branch matched");
    }

    @Test
    void conditionsResolveNodePlaceholders() {
        ExecutionContext ctx = ctx();
        ctx.storeNodeMessage("er", Fixtures.fields(39, "2"));
        NodeHandlerResult r = runBranches(ctx, java.util.List.of(
                branch("b1", "Filled", java.util.List.of("{{node:er:tag39}} == 2"), "n-filled"),
                branch("b2", "Other", java.util.List.of(), "n-default")));
        assertThat(r.nextNodeId()).isEqualTo("n-filled");
    }

    @Test
    void theMatchedBranchIsRecordedForTheEventLog() {
        ExecutionContext ctx = ctx();
        runBranches(ctx, java.util.List.of(branch("b1", "Filled", java.util.List.of("1 == 1"), "n-filled")));
        assertThat(ctx.getVariable("node:d:matchedBranchId")).isEqualTo("b1");
        assertThat(ctx.getVariable("node:d:matchedBranchLabel")).isEqualTo("Filled");
    }

    @Test
    void anUnlabelledBranchIsRecordedByItsId() {
        ExecutionContext ctx = ctx();
        runBranches(ctx, java.util.List.of(branch("b1", "", java.util.List.of("1 == 1"), "n-filled")));
        assertThat(ctx.getVariable("node:d:matchedBranchLabel")).isEqualTo("b1");
    }

    @Test
    void anEmptyBranchListFallsBackToTheLegacyCondition() {
        NodeHandlerResult r = handler.handle(node("d", NodeType.DECISION)
                .cfg("branches", java.util.List.of())
                .cfg("condition", "foo == foo")
                .onSuccess("ok").onFailure("no").build(), ctx());
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("ok");
    }
}
