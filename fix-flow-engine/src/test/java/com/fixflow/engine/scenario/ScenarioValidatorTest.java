package com.fixflow.engine.scenario;

import com.fixflow.core.domain.scenario.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ScenarioValidator}.
 */
class ScenarioValidatorTest {

    private final ScenarioValidator validator = new ScenarioValidator();

    // Helper to build a minimal valid scenario with a single START node
    private Scenario minimalValid() {
        return new Scenario(
            UUID.randomUUID(), "test", "", "1.0", "sess",
            RuntimePolicy.PARALLEL, List.of(), List.of(),
            List.of(
                new ScenarioNode("n1", "start", NodeType.START, Map.of(), null, null, null, null, null)
            ),
            List.of(), Map.of()
        );
    }

    @Test
    void validMinimalScenario_returnsNoErrors() {
        List<String> errors = validator.validate(minimalValid());
        assertThat(errors).isEmpty();
    }

    @Test
    void scenarioWithNoStartNode_reportsError() {
        Scenario s = new Scenario(
            UUID.randomUUID(), "no-start", "", "1.0", "sess",
            RuntimePolicy.PARALLEL, List.of(), List.of(),
            List.of(
                new ScenarioNode("n1", "send", NodeType.SEND_FIX, Map.of(), null, null, null, null, null)
            ),
            List.of(), Map.of()
        );
        List<String> errors = validator.validate(s);
        assertThat(errors).anyMatch(e -> e.contains("START"));
    }

    @Test
    void duplicateNodeId_reportsError() {
        Scenario s = new Scenario(
            UUID.randomUUID(), "dup-nodes", "", "1.0", "sess",
            RuntimePolicy.PARALLEL, List.of(), List.of(),
            List.of(
                new ScenarioNode("n1", "start", NodeType.START, Map.of(), null, null, "n2", null, null),
                new ScenarioNode("n1", "send",  NodeType.SEND_FIX, Map.of(), null, null, null, null, null)
            ),
            List.of(), Map.of()
        );
        List<String> errors = validator.validate(s);
        assertThat(errors).anyMatch(e -> e.contains("Duplicate") && e.contains("n1"));
    }

    @Test
    void onSuccess_referencingMissingNode_reportsError() {
        Scenario s = new Scenario(
            UUID.randomUUID(), "bad-ref", "", "1.0", "sess",
            RuntimePolicy.PARALLEL, List.of(), List.of(),
            List.of(
                new ScenarioNode("n1", "start", NodeType.START, Map.of(), null, null, "GHOST", null, null)
            ),
            List.of(), Map.of()
        );
        List<String> errors = validator.validate(s);
        assertThat(errors).anyMatch(e -> e.contains("onSuccess") && e.contains("GHOST"));
    }

    @Test
    void onFailure_referencingMissingNode_reportsError() {
        Scenario s = new Scenario(
            UUID.randomUUID(), "bad-fail-ref", "", "1.0", "sess",
            RuntimePolicy.PARALLEL, List.of(), List.of(),
            List.of(
                new ScenarioNode("n1", "start", NodeType.START, Map.of(), null, null, null, "GHOST_F", null)
            ),
            List.of(), Map.of()
        );
        List<String> errors = validator.validate(s);
        assertThat(errors).anyMatch(e -> e.contains("onFailure") && e.contains("GHOST_F"));
    }

    @Test
    void onTimeout_referencingMissingNode_reportsError() {
        Scenario s = new Scenario(
            UUID.randomUUID(), "bad-timeout-ref", "", "1.0", "sess",
            RuntimePolicy.PARALLEL, List.of(), List.of(),
            List.of(
                new ScenarioNode("n1", "start", NodeType.START, Map.of(), null, null, null, null, "GHOST_T")
            ),
            List.of(), Map.of()
        );
        List<String> errors = validator.validate(s);
        assertThat(errors).anyMatch(e -> e.contains("onTimeout") && e.contains("GHOST_T"));
    }

    @Test
    void edgeFromUnknownNode_reportsError() {
        Scenario s = new Scenario(
            UUID.randomUUID(), "bad-edge-from", "", "1.0", "sess",
            RuntimePolicy.PARALLEL, List.of(), List.of(),
            List.of(
                new ScenarioNode("n1", "start", NodeType.START, Map.of(), null, null, null, null, null)
            ),
            List.of(new ScenarioEdge("GHOST", "n1", "default")),
            Map.of()
        );
        List<String> errors = validator.validate(s);
        assertThat(errors).anyMatch(e -> e.contains("Edge from") && e.contains("GHOST"));
    }

    @Test
    void edgeToUnknownNode_reportsError() {
        Scenario s = new Scenario(
            UUID.randomUUID(), "bad-edge-to", "", "1.0", "sess",
            RuntimePolicy.PARALLEL, List.of(), List.of(),
            List.of(
                new ScenarioNode("n1", "start", NodeType.START, Map.of(), null, null, null, null, null)
            ),
            List.of(new ScenarioEdge("n1", "GHOST_TO", "default")),
            Map.of()
        );
        List<String> errors = validator.validate(s);
        assertThat(errors).anyMatch(e -> e.contains("Edge to") && e.contains("GHOST_TO"));
    }

    @Test
    void validFullScenario_returnsNoErrors() {
        Scenario s = new Scenario(
            UUID.randomUUID(), "full-valid", "", "1.0", "sess",
            RuntimePolicy.PARALLEL,
            List.of(),
            List.of(new CorrelationRule(131, "n3", 131, 5000)),
            List.of(
                new ScenarioNode("n1", "start",  NodeType.START,    Map.of(), null, null, "n2", null, null),
                new ScenarioNode("n2", "send",   NodeType.SEND_FIX, Map.of(), null, null, "n3", null, null),
                new ScenarioNode("n3", "expect", NodeType.EXPECT_FIX, Map.of(),
                    new TimeoutConfig(5, TimeUnit.SECONDS, TimeoutAction.FAIL, null),
                    null, "n4", "nf", null),
                new ScenarioNode("n4", "done",   NodeType.END_PASS, Map.of(), null, null, null, null, null),
                new ScenarioNode("nf", "fail",   NodeType.END_FAIL, Map.of(), null, null, null, null, null)
            ),
            List.of(
                new ScenarioEdge("n1", "n2", "start"),
                new ScenarioEdge("n2", "n3", "sent"),
                new ScenarioEdge("n3", "n4", "success"),
                new ScenarioEdge("n3", "nf", "fail")
            ),
            Map.of()
        );
        List<String> errors = validator.validate(s);
        assertThat(errors).isEmpty();
    }

    @Test
    void multipleErrors_allReported() {
        // No START, duplicate id, bad onSuccess reference
        Scenario s = new Scenario(
            UUID.randomUUID(), "multi-err", "", "1.0", "sess",
            RuntimePolicy.PARALLEL, List.of(), List.of(),
            List.of(
                new ScenarioNode("n1", "send",  NodeType.SEND_FIX, Map.of(), null, null, "GONE", null, null),
                new ScenarioNode("n1", "send2", NodeType.SEND_FIX, Map.of(), null, null, null, null, null)
            ),
            List.of(), Map.of()
        );
        List<String> errors = validator.validate(s);
        // Should contain: no START, duplicate n1, bad onSuccess GONE
        assertThat(errors.size()).isGreaterThanOrEqualTo(3);
    }
}
