package com.fixflow.core.domain.scenario;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioDomainTest {

    private ScenarioNode node(String id, NodeType type) {
        return new ScenarioNode(id, id, type, Map.of(), null, null, null, null, null);
    }

    private Scenario scenarioWith(List<ScenarioNode> nodes) {
        return new Scenario(UUID.randomUUID(), "demo", "desc", "1.0", "sess",
                RuntimePolicy.SEQUENTIAL, List.of(), List.of(), nodes, List.of(),
                Map.of(), "yaml");
    }

    // ---------- Scenario ----------

    @Test
    void scenarioKeepsAccessors() {
        UUID id = UUID.randomUUID();
        ScenarioNode n = node("n1", NodeType.START);
        RoutingRule rr = new RoutingRule(Map.of("35", "D"), "sc", 1);
        CorrelationRule cr = new CorrelationRule(11, "n1", 37, 5000);
        ScenarioEdge edge = new ScenarioEdge("n1", "n2", "ok");
        VariableDef vd = new VariableDef("string", "def");

        Scenario s = new Scenario(id, "name", "desc", "2.0", "sess",
                RuntimePolicy.EXCLUSIVE, List.of(rr), List.of(cr),
                List.of(n), List.of(edge), Map.of("v", vd), "raw");

        assertThat(s.id()).isEqualTo(id);
        assertThat(s.name()).isEqualTo("name");
        assertThat(s.description()).isEqualTo("desc");
        assertThat(s.version()).isEqualTo("2.0");
        assertThat(s.sessionRef()).isEqualTo("sess");
        assertThat(s.runtimePolicy()).isEqualTo(RuntimePolicy.EXCLUSIVE);
        assertThat(s.routingRules()).containsExactly(rr);
        assertThat(s.correlationRules()).containsExactly(cr);
        assertThat(s.nodes()).containsExactly(n);
        assertThat(s.edges()).containsExactly(edge);
        assertThat(s.variables()).containsEntry("v", vd);
        assertThat(s.rawYaml()).isEqualTo("raw");
    }

    @Test
    void scenarioRejectsNullId() {
        assertThatThrownBy(() -> new Scenario(null, "n", null, null, null,
                null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scenario id required");
    }

    @Test
    void scenarioRejectsNullName() {
        assertThatThrownBy(() -> new Scenario(UUID.randomUUID(), null, null, null, null,
                null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scenario name required");
    }

    @Test
    void scenarioRejectsBlankName() {
        assertThatThrownBy(() -> new Scenario(UUID.randomUUID(), "   ", null, null, null,
                null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scenario name required");
    }

    @Test
    void scenarioDefaultsNullCollectionsToEmpty() {
        Scenario s = new Scenario(UUID.randomUUID(), "n", null, null, null,
                RuntimePolicy.PARALLEL, null, null, null, null, null, null);
        assertThat(s.nodes()).isEmpty();
        assertThat(s.edges()).isEmpty();
        assertThat(s.routingRules()).isEmpty();
        assertThat(s.correlationRules()).isEmpty();
        assertThat(s.variables()).isEmpty();
    }

    @Test
    void scenarioDefensivelyCopiesNodes() {
        List<ScenarioNode> nodes = new ArrayList<>();
        nodes.add(node("n1", NodeType.START));
        Scenario s = scenarioWith(nodes);
        nodes.add(node("n2", NodeType.END_PASS));
        assertThat(s.nodes()).hasSize(1);
        assertThatThrownBy(() -> s.nodes().add(node("x", NodeType.WAIT)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void findNodeReturnsMatchWhenPresent() {
        ScenarioNode start = node("n1", NodeType.START);
        ScenarioNode end = node("n2", NodeType.END_PASS);
        Scenario s = scenarioWith(List.of(start, end));
        assertThat(s.findNode("n1")).contains(start);
        assertThat(s.findNode("n2")).contains(end);
    }

    @Test
    void findNodeReturnsEmptyWhenAbsent() {
        Scenario s = scenarioWith(List.of(node("n1", NodeType.START)));
        assertThat(s.findNode("missing")).isEmpty();
    }

    @Test
    void findNodeReturnsEmptyForNullId() {
        Scenario s = scenarioWith(List.of(node("n1", NodeType.START)));
        assertThat(s.findNode(null)).isEmpty();
    }

    @Test
    void startNodeFindsStartType() {
        ScenarioNode start = node("s", NodeType.START);
        Scenario s = scenarioWith(List.of(node("a", NodeType.WAIT), start, node("b", NodeType.END_PASS)));
        assertThat(s.startNode()).contains(start);
    }

    @Test
    void startNodeEmptyWhenNoStart() {
        Scenario s = scenarioWith(List.of(node("a", NodeType.WAIT), node("b", NodeType.END_PASS)));
        assertThat(s.startNode()).isEmpty();
    }

    // ---------- ScenarioNode ----------

    @Test
    void scenarioNodeKeepsAccessors() {
        Map<String, Object> cfg = Map.of("k", "v");
        TimeoutConfig to = new TimeoutConfig(5, TimeUnit.SECONDS, TimeoutAction.FAIL, null);
        RetryPolicy rp = new RetryPolicy(3, 100);
        ScenarioNode n = new ScenarioNode("id", "name", NodeType.EXPECT_FIX, cfg,
                to, rp, "succ", "fail", "tout");

        assertThat(n.id()).isEqualTo("id");
        assertThat(n.name()).isEqualTo("name");
        assertThat(n.type()).isEqualTo(NodeType.EXPECT_FIX);
        assertThat(n.config()).containsEntry("k", "v");
        assertThat(n.timeout()).isEqualTo(to);
        assertThat(n.retryPolicy()).isEqualTo(rp);
        assertThat(n.onSuccess()).isEqualTo("succ");
        assertThat(n.onFailure()).isEqualTo("fail");
        assertThat(n.onTimeout()).isEqualTo("tout");
    }

    @Test
    void scenarioNodeRejectsNullId() {
        assertThatThrownBy(() -> new ScenarioNode(null, "n", NodeType.START, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("node id required");
    }

    @Test
    void scenarioNodeRejectsBlankId() {
        assertThatThrownBy(() -> new ScenarioNode("  ", "n", NodeType.START, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("node id required");
    }

    @Test
    void scenarioNodeRejectsNullType() {
        assertThatThrownBy(() -> new ScenarioNode("id", "n", null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("node type required");
    }

    @Test
    void scenarioNodeDefaultsAndCopiesConfig() {
        ScenarioNode nullCfg = new ScenarioNode("id", "n", NodeType.START, null, null, null, null, null, null);
        assertThat(nullCfg.config()).isEmpty();

        Map<String, Object> cfg = new HashMap<>(Map.of("a", 1));
        ScenarioNode n = new ScenarioNode("id", "n", NodeType.START, cfg, null, null, null, null, null);
        cfg.put("b", 2);
        assertThat(n.config()).containsOnlyKeys("a");
        assertThatThrownBy(() -> n.config().put("z", 0)).isInstanceOf(UnsupportedOperationException.class);
    }

    // ---------- ScenarioEdge ----------

    @Test
    void scenarioEdgeKeepsAccessors() {
        ScenarioEdge e = new ScenarioEdge("a", "b", "lbl");
        assertThat(e.from()).isEqualTo("a");
        assertThat(e.to()).isEqualTo("b");
        assertThat(e.label()).isEqualTo("lbl");
    }

    @Test
    void scenarioEdgeAllowsNullLabel() {
        assertThat(new ScenarioEdge("a", "b", null).label()).isNull();
    }

    @Test
    void scenarioEdgeRejectsNullFrom() {
        assertThatThrownBy(() -> new ScenarioEdge(null, "b", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("edge from required");
    }

    @Test
    void scenarioEdgeRejectsBlankFrom() {
        assertThatThrownBy(() -> new ScenarioEdge(" ", "b", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("edge from required");
    }

    @Test
    void scenarioEdgeRejectsNullTo() {
        assertThatThrownBy(() -> new ScenarioEdge("a", null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("edge to required");
    }

    @Test
    void scenarioEdgeRejectsBlankTo() {
        assertThatThrownBy(() -> new ScenarioEdge("a", "  ", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("edge to required");
    }

    // ---------- TimeoutConfig ----------

    @Test
    void timeoutConfigKeepsAccessors() {
        TimeoutConfig tc = new TimeoutConfig(5, TimeUnit.SECONDS, TimeoutAction.JUMP, "n2");
        assertThat(tc.value()).isEqualTo(5);
        assertThat(tc.unit()).isEqualTo(TimeUnit.SECONDS);
        assertThat(tc.onTimeout()).isEqualTo(TimeoutAction.JUMP);
        assertThat(tc.jumpTo()).isEqualTo("n2");
    }

    @Test
    void timeoutConfigToMillisCoversAllUnits() {
        assertThat(new TimeoutConfig(500, TimeUnit.MILLISECONDS, TimeoutAction.FAIL, null).toMillis()).isEqualTo(500);
        assertThat(new TimeoutConfig(2, TimeUnit.SECONDS, TimeoutAction.FAIL, null).toMillis()).isEqualTo(2_000);
        assertThat(new TimeoutConfig(3, TimeUnit.MINUTES, TimeoutAction.FAIL, null).toMillis()).isEqualTo(180_000);
        assertThat(new TimeoutConfig(1, TimeUnit.HOURS, TimeoutAction.FAIL, null).toMillis()).isEqualTo(3_600_000);
        assertThat(new TimeoutConfig(1, TimeUnit.DAYS, TimeoutAction.FAIL, null).toMillis()).isEqualTo(86_400_000);
    }

    @Test
    void timeoutConfigToMillisOverflowThrows() {
        TimeoutConfig tc = new TimeoutConfig(Long.MAX_VALUE, TimeUnit.DAYS, TimeoutAction.FAIL, null);
        assertThatThrownBy(tc::toMillis).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void timeoutConfigRejectsNullUnit() {
        assertThatThrownBy(() -> new TimeoutConfig(1, null, TimeoutAction.FAIL, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unit required");
    }

    @Test
    void timeoutConfigRejectsNullOnTimeout() {
        assertThatThrownBy(() -> new TimeoutConfig(1, TimeUnit.SECONDS, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("onTimeout required");
    }

    @Test
    void timeoutConfigRejectsNegativeValue() {
        assertThatThrownBy(() -> new TimeoutConfig(-1, TimeUnit.SECONDS, TimeoutAction.FAIL, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("value must be >= 0");
    }

    @Test
    void timeoutConfigRejectsJumpWithoutTarget() {
        assertThatThrownBy(() -> new TimeoutConfig(1, TimeUnit.SECONDS, TimeoutAction.JUMP, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("jumpTo required");
        assertThatThrownBy(() -> new TimeoutConfig(1, TimeUnit.SECONDS, TimeoutAction.JUMP, "  "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("jumpTo required");
    }

    @Test
    void timeoutConfigAllowsZeroValue() {
        assertThat(new TimeoutConfig(0, TimeUnit.SECONDS, TimeoutAction.CONTINUE, null).toMillis()).isZero();
    }

    // ---------- RetryPolicy ----------

    @Test
    void retryPolicyKeepsAccessors() {
        RetryPolicy rp = new RetryPolicy(4, 250);
        assertThat(rp.maxAttempts()).isEqualTo(4);
        assertThat(rp.delayMs()).isEqualTo(250);
    }

    @Test
    void retryPolicyAllowsZeros() {
        RetryPolicy rp = new RetryPolicy(0, 0);
        assertThat(rp.maxAttempts()).isZero();
        assertThat(rp.delayMs()).isZero();
    }

    @Test
    void retryPolicyRejectsNegativeMaxAttempts() {
        assertThatThrownBy(() -> new RetryPolicy(-1, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxAttempts must be >= 0");
    }

    @Test
    void retryPolicyRejectsNegativeDelay() {
        assertThatThrownBy(() -> new RetryPolicy(1, -5))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("delayMs must be >= 0");
    }

    // ---------- RoutingRule ----------

    @Test
    void routingRuleKeepsAccessors() {
        RoutingRule rr = new RoutingRule(Map.of("35", "D"), "sc-1", 7);
        assertThat(rr.criteria()).containsEntry("35", "D");
        assertThat(rr.scenarioId()).isEqualTo("sc-1");
        assertThat(rr.priority()).isEqualTo(7);
    }

    @Test
    void routingRuleDefaultsAndCopiesCriteria() {
        assertThat(new RoutingRule(null, "sc", 1).criteria()).isEmpty();
        Map<String, String> crit = new HashMap<>(Map.of("a", "b"));
        RoutingRule rr = new RoutingRule(crit, "sc", 1);
        crit.put("c", "d");
        assertThat(rr.criteria()).containsOnlyKeys("a");
        assertThatThrownBy(() -> rr.criteria().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    // ---------- CorrelationRule ----------

    @Test
    void correlationRuleKeepsAccessors() {
        CorrelationRule cr = new CorrelationRule(11, "n1", 37, 3000);
        assertThat(cr.sourceTag()).isEqualTo(11);
        assertThat(cr.targetNode()).isEqualTo("n1");
        assertThat(cr.targetTag()).isEqualTo(37);
        assertThat(cr.timeWindowMs()).isEqualTo(3000);
    }

    // ---------- VariableDef ----------

    @Test
    void variableDefKeepsAccessors() {
        VariableDef vd = new VariableDef("int", "42");
        assertThat(vd.type()).isEqualTo("int");
        assertThat(vd.defaultValue()).isEqualTo("42");
        assertThat(vd).isEqualTo(new VariableDef("int", "42"));
    }

    // ---------- enums ----------

    @Test
    void nodeTypeEnumValues() {
        assertThat(NodeType.values()).containsExactly(
                NodeType.START, NodeType.SEND_FIX, NodeType.EXPECT_FIX, NodeType.VALIDATE,
                NodeType.WAIT, NodeType.TIMEOUT, NodeType.DECISION, NodeType.BRANCH,
                NodeType.RETRY, NodeType.LOOP, NodeType.DELAY, NodeType.END_PASS,
                NodeType.END_FAIL, NodeType.HTTP_REQUEST, NodeType.ROUTE_FIX, NodeType.CALL_SCENARIO);
        assertThat(NodeType.valueOf("CALL_SCENARIO")).isEqualTo(NodeType.CALL_SCENARIO);
    }

    @Test
    void runtimePolicyEnumValues() {
        assertThat(RuntimePolicy.values()).containsExactly(
                RuntimePolicy.PARALLEL, RuntimePolicy.SEQUENTIAL, RuntimePolicy.EXCLUSIVE);
    }

    @Test
    void timeoutActionEnumValues() {
        assertThat(TimeoutAction.values()).containsExactly(
                TimeoutAction.FAIL, TimeoutAction.RETRY, TimeoutAction.CONTINUE, TimeoutAction.JUMP);
    }

    @Test
    void timeUnitEnumValues() {
        assertThat(TimeUnit.values()).containsExactly(
                TimeUnit.MILLISECONDS, TimeUnit.SECONDS, TimeUnit.MINUTES, TimeUnit.HOURS, TimeUnit.DAYS);
    }

    @Test
    void allEnumsSupportValueOfRoundTrip() {
        for (NodeType v : NodeType.values()) assertThat(NodeType.valueOf(v.name())).isEqualTo(v);
        for (RuntimePolicy v : RuntimePolicy.values()) assertThat(RuntimePolicy.valueOf(v.name())).isEqualTo(v);
        for (TimeoutAction v : TimeoutAction.values()) assertThat(TimeoutAction.valueOf(v.name())).isEqualTo(v);
        for (TimeUnit v : TimeUnit.values()) assertThat(TimeUnit.valueOf(v.name())).isEqualTo(v);
    }
}
