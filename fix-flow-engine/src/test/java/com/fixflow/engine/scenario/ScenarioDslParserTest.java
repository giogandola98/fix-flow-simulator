package com.fixflow.engine.scenario;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioDslParserTest {

    private static final String MINIMAL_YAML = """
            id: 11111111-1111-1111-1111-111111111111
            name: minimal-demo
            description: minimal smoke
            version: '1.0'
            sessionRef: sess-1
            runtimePolicy: PARALLEL
            nodes:
              - id: n1
                name: Start
                type: START
                onSuccess: n2
              - id: n2
                name: Send NewOrderSingle
                type: SEND_FIX
                config:
                  msgType: D
                  fields:
                    11: REQ-001
                    55: AAPL
                timeout:
                  value: 5
                  unit: SECONDS
                  onTimeout: FAIL
                onSuccess: n3
              - id: n3
                name: Done
                type: END_PASS
            edges:
              - from: n1
                to: n2
              - from: n2
                to: n3
            """;

    @Test
    void parsesMinimalYaml() {
        Scenario s = new ScenarioDslParser().parseYaml(MINIMAL_YAML);

        assertThat(s.name()).isEqualTo("minimal-demo");
        assertThat(s.nodes()).hasSize(3);
        assertThat(s.findNode("n2")).isPresent();
        assertThat(s.findNode("n2").orElseThrow().type()).isEqualTo(NodeType.SEND_FIX);
        assertThat(s.findNode("n2").orElseThrow().config()).containsKey("fields");
        assertThat(s.findNode("n2").orElseThrow().timeout().value()).isEqualTo(5);
        assertThat(s.findNode("n2").orElseThrow().timeout().unit()).isEqualTo(TimeUnit.SECONDS);
    }

    @Test
    void roundTripYamlSerialization() {
        ScenarioDslParser parser = new ScenarioDslParser();
        Scenario original = parser.parseYaml(MINIMAL_YAML);

        String yamlOut = parser.toYaml(original);
        Scenario reparsed = parser.parseYaml(yamlOut);

        assertThat(reparsed).isEqualTo(original);
    }
}
