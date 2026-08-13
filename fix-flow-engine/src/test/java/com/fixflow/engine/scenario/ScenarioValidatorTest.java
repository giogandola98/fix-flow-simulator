package com.fixflow.engine.scenario;

import com.fixflow.core.domain.scenario.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioValidatorTest {

    private ScenarioValidator validator;

    @BeforeEach
    void setUp() { validator = new ScenarioValidator(); }

    private ScenarioNode n(String id, NodeType type, String onSuccess, String onFailure, String onTimeout) {
        return new ScenarioNode(id, id, type, Map.of(), null, null, onSuccess, onFailure, onTimeout);
    }

    private Scenario scenario(List<ScenarioNode> nodes, List<ScenarioEdge> edges) {
        return new Scenario(UUID.randomUUID(), "s", "", "1", "sess",
                RuntimePolicy.PARALLEL, List.of(), List.of(), nodes, edges, Map.of(), null);
    }

    @Test
    void validScenarioHasNoErrors() {
        Scenario s = scenario(List.of(
                n("start", NodeType.START, "end", null, null),
                n("end", NodeType.END_PASS, null, null, null)),
                List.of(new ScenarioEdge("start", "end", null)));
        assertThat(validator.validate(s)).isEmpty();
    }

    @Test
    void missingStartReported() {
        Scenario s = scenario(List.of(n("end", NodeType.END_PASS, null, null, null)), List.of());
        assertThat(validator.validate(s)).anyMatch(e -> e.contains("no START node"));
    }

    @Test
    void duplicateNodeIdReported() {
        Scenario s = scenario(List.of(
                n("start", NodeType.START, null, null, null),
                n("start", NodeType.END_PASS, null, null, null)),
                List.of());
        assertThat(validator.validate(s)).anyMatch(e -> e.contains("Duplicate node id: start"));
    }

    @Test
    void danglingOnSuccessReported() {
        Scenario s = scenario(List.of(n("start", NodeType.START, "ghost", null, null)), List.of());
        assertThat(validator.validate(s)).anyMatch(e -> e.contains("onSuccess references unknown node: ghost"));
    }

    @Test
    void danglingOnFailureReported() {
        Scenario s = scenario(List.of(n("start", NodeType.START, null, "ghost", null)), List.of());
        assertThat(validator.validate(s)).anyMatch(e -> e.contains("onFailure references unknown node: ghost"));
    }

    @Test
    void danglingOnTimeoutReported() {
        Scenario s = scenario(List.of(n("start", NodeType.START, null, null, "ghost")), List.of());
        assertThat(validator.validate(s)).anyMatch(e -> e.contains("onTimeout references unknown node: ghost"));
    }

    @Test
    void danglingEdgeReferencesReported() {
        Scenario s = scenario(List.of(n("start", NodeType.START, null, null, null)),
                List.of(new ScenarioEdge("start", "ghost", null), new ScenarioEdge("phantom", "start", null)));
        List<String> errors = validator.validate(s);
        assertThat(errors).anyMatch(e -> e.contains("Edge to unknown node: ghost"));
        assertThat(errors).anyMatch(e -> e.contains("Edge from unknown node: phantom"));
    }
}
