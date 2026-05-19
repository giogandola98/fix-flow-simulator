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
    void parsesCustomPrivateTagsAbove9999() {
        String yaml = """
                id: 22222222-2222-2222-2222-222222222222
                name: custom-tags
                description: custom tag test
                version: '1.0'
                sessionRef: sess-1
                runtimePolicy: PARALLEL
                nodes:
                  - id: n1
                    name: Start
                    type: START
                    onSuccess: n2
                  - id: n2
                    name: Send with custom tag
                    type: SEND_FIX
                    config:
                      msgType: D
                      fields:
                        11: CL-001
                        500006: private-val
                    onSuccess: n3
                  - id: n3
                    name: Done
                    type: END_PASS
                edges: []
                """;

        Scenario s = new ScenarioDslParser().parseYaml(yaml);

        @SuppressWarnings("unchecked")
        java.util.Map<Object, Object> fields =
                (java.util.Map<Object, Object>) s.findNode("n2").orElseThrow().config().get("fields");
        // key may be Integer or String depending on YAML parser; both convert to tag 500006
        boolean found = fields.entrySet().stream()
                .anyMatch(e -> Integer.parseInt(e.getKey().toString()) == 500006
                        && "private-val".equals(e.getValue()));
        assertThat(found).as("tag 500006 parsed from YAML").isTrue();
    }

    @Test
    void roundTripYamlSerialization() {
        ScenarioDslParser parser = new ScenarioDslParser();
        Scenario original = parser.parseYaml(MINIMAL_YAML);

        String yamlOut = parser.toYaml(original);
        Scenario reparsed = parser.parseYaml(yamlOut);

        assertThat(reparsed).usingRecursiveComparison().ignoringFields("rawYaml").isEqualTo(original);
    }
}
