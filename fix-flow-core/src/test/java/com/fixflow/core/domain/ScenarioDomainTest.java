package com.fixflow.core.domain;

import com.fixflow.core.domain.scenario.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioDomainTest {

    @Test
    void scenarioExposesNodeLookupById() {
        ScenarioNode start = new ScenarioNode(
            "n1", "Start", NodeType.START, Map.of(), null, null, "n2", null, null);
        ScenarioNode end = new ScenarioNode(
            "n2", "End", NodeType.END_PASS, Map.of(), null, null, null, null, null);

        Scenario scenario = new Scenario(
            UUID.randomUUID(), "demo", "test scenario", "1.0", "sess-1",
            RuntimePolicy.PARALLEL, List.of(), List.of(),
            List.of(start, end),
            List.of(new ScenarioEdge("n1", "n2", "ok")),
            Map.of());

        assertThat(scenario.findNode("n1")).contains(start);
        assertThat(scenario.findNode("n2")).contains(end);
        assertThat(scenario.findNode("missing")).isEmpty();
        assertThat(scenario.nodes()).hasSize(2);
    }

    @Test
    void timeoutConfigUsesEnumUnits() {
        TimeoutConfig tc = new TimeoutConfig(5, TimeUnit.SECONDS, TimeoutAction.FAIL, null);
        assertThat(tc.value()).isEqualTo(5);
        assertThat(tc.unit()).isEqualTo(TimeUnit.SECONDS);
        assertThat(tc.onTimeout()).isEqualTo(TimeoutAction.FAIL);
    }
}
