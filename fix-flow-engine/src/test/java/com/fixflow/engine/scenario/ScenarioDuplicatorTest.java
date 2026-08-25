package com.fixflow.engine.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.ScenarioNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioDuplicatorTest {

    private static final String SOURCE = """
            id: 96685ac6-4bbf-4c5f-89c2-198b9d39575b
            name: NOrder - MKT - Accept
            description: ''
            version: '1'
            sessionRef: default
            nodes:
              - id: start-1
                name: Start
                type: START
                config: {}
                onSuccess: expect-1
                position: {x: -155.2, y: -64.6}
              - id: expect-1
                name: NEW ORDER SINGLE
                type: EXPECT_FIX
                config:
                  msgType: D
                  correlation:
                    sourceTag: 11
                    fromNode: send-1
                    targetTag: 11
                timeout: {value: 30, unit: MINUTES, onTimeout: JUMP, jumpTo: end-fail}
                onSuccess: validate-1
                onFailure: end-fail
                position: {x: 225.2, y: 11.8}
              - id: validate-1
                name: CHECK FIELDS
                type: VALIDATE
                config:
                  sourceNodeId: expect-1
                  rules:
                    - {tag: 55, rule: FIELD_PRESENT}
                  dateRules:
                    - {ruleId: dr-1, type: FIELD_OFFSET, sourceNode: expect-1, sourceTag: 60}
                onSuccess: route-1
                onFailure: end-fail
                position: {x: 225.1, y: 149.4}
              - id: route-1
                name: ROUTE
                type: ROUTE_FIX
                config:
                  rules:
                    - {ruleId: r1, label: filled, matchers: {39: '2'}, targetNodeId: send-1}
                position: {x: 225.7, y: 262.6}
              - id: send-1
                name: ORDER ACCEPTED
                type: SEND_FIX
                config:
                  msgType: '8'
                  fields:
                    11: '{{node:expect-1:tag11}}'
                    37: '{{node:expect-1:offset:tag11}}'
                onSuccess: end-pass
                position: {x: 229.1, y: 376.4}
              - id: end-pass
                name: End
                type: END_PASS
                config: {}
                position: {x: -171.3, y: 537.1}
              - id: end-fail
                name: Failed
                type: END_FAIL
                config: {}
                position: {x: 400.0, y: 537.1}
            edges:
              - {from: start-1, to: expect-1, label: success}
              - {from: expect-1, to: validate-1, label: success}
              - {from: validate-1, to: route-1, label: success}
              - {from: validate-1, to: end-fail, label: failure, sourceHandle: failure}
              - {from: route-1, to: send-1, label: filled, sourceHandle: r1}
              - {from: send-1, to: end-pass, label: success}
            """;

    private final ScenarioDuplicator duplicator = new ScenarioDuplicator();
    private final ScenarioDslParser parser = new ScenarioDslParser();
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    private JsonNode copy;

    @BeforeEach
    void setUp() throws Exception {
        copy = yaml.readTree(duplicator.duplicate(SOURCE, "NOrder - MKT - Accept (copy)"));
    }

    private JsonNode node(int i) { return copy.get("nodes").get(i); }

    @Test
    void givesTheCopyANewScenarioIdAndName() {
        assertThat(copy.get("id").asText()).isNotEqualTo("96685ac6-4bbf-4c5f-89c2-198b9d39575b");
        assertThat(UUID.fromString(copy.get("id").asText())).isNotNull();
        assertThat(copy.get("name").asText()).isEqualTo("NOrder - MKT - Accept (copy)");
    }

    @Test
    void givesEveryNodeANewId() {
        for (JsonNode n : copy.get("nodes")) {
            assertThat(n.get("id").asText()).doesNotContain("start-1", "expect-1", "validate-1",
                    "route-1", "send-1", "end-pass", "end-fail");
            assertThat(UUID.fromString(n.get("id").asText())).isNotNull();
        }
        assertThat(copy.get("nodes")).hasSize(7);
    }

    @Test
    void rewritesBranchTargetsAndTimeoutJump() {
        String expectId = node(1).get("id").asText();
        String validateId = node(2).get("id").asText();
        String endFailId = node(6).get("id").asText();

        assertThat(node(0).get("onSuccess").asText()).isEqualTo(expectId);
        assertThat(node(1).get("onSuccess").asText()).isEqualTo(validateId);
        assertThat(node(1).get("onFailure").asText()).isEqualTo(endFailId);
        assertThat(node(1).get("timeout").get("jumpTo").asText()).isEqualTo(endFailId);
    }

    @Test
    void rewritesConfigLevelNodeReferences() {
        String expectId = node(1).get("id").asText();
        String sendId = node(4).get("id").asText();

        // EXPECT_FIX correlation.fromNode
        assertThat(node(1).get("config").get("correlation").get("fromNode").asText()).isEqualTo(sendId);
        // VALIDATE sourceNodeId and a date rule's sourceNode
        assertThat(node(2).get("config").get("sourceNodeId").asText()).isEqualTo(expectId);
        assertThat(node(2).get("config").get("dateRules").get(0).get("sourceNode").asText()).isEqualTo(expectId);
        // ROUTE_FIX rule target
        assertThat(node(3).get("config").get("rules").get(0).get("targetNodeId").asText()).isEqualTo(sendId);
    }

    @Test
    void rewritesNodePlaceholdersIncludingTheOffsetVariant() {
        String expectId = node(1).get("id").asText();
        JsonNode fields = node(4).get("config").get("fields");
        assertThat(fields.get("11").asText()).isEqualTo("{{node:" + expectId + ":tag11}}");
        assertThat(fields.get("37").asText()).isEqualTo("{{node:" + expectId + ":offset:tag11}}");
    }

    @Test
    void rewritesTheVisualEdgesAndKeepsTheirHandles() {
        String validateId = node(2).get("id").asText();
        String endFailId = node(6).get("id").asText();

        JsonNode failureEdge = copy.get("edges").get(3);
        assertThat(failureEdge.get("from").asText()).isEqualTo(validateId);
        assertThat(failureEdge.get("to").asText()).isEqualTo(endFailId);
        assertThat(failureEdge.get("label").asText()).isEqualTo("failure");
        assertThat(failureEdge.get("sourceHandle").asText()).isEqualTo("failure");
        // a ROUTE_FIX rule id is not a node id and must survive untouched
        assertThat(copy.get("edges").get(4).get("sourceHandle").asText()).isEqualTo("r1");
    }

    @Test
    void keepsEverythingElseIncludingLayoutAndUnmodelledKeys() {
        assertThat(node(0).get("position").get("x").asDouble()).isEqualTo(-155.2);
        assertThat(node(5).get("position").get("y").asDouble()).isEqualTo(537.1);
        assertThat(copy.get("sessionRef").asText()).isEqualTo("default");
        assertThat(copy.get("version").asText()).isEqualTo("1");
        assertThat(node(1).get("timeout").get("unit").asText()).isEqualTo("MINUTES");
    }

    @Test
    void theCopyStillParsesIntoAWorkingScenario() {
        Scenario s = parser.parseYaml(duplicator.duplicate(SOURCE, "copy"));
        assertThat(s.nodes()).hasSize(7);
        ScenarioNode start = s.startNode().orElseThrow();
        assertThat(s.findNode(start.onSuccess())).isPresent();
        assertThat(s.id()).isNotEqualTo(UUID.fromString("96685ac6-4bbf-4c5f-89c2-198b9d39575b"));
    }

    @Test
    void twoCopiesOfTheSameScenarioShareNoIds() {
        String a = duplicator.duplicate(SOURCE, "a");
        String b = duplicator.duplicate(SOURCE, "b");
        assertThat(parser.parseYaml(a).id()).isNotEqualTo(parser.parseYaml(b).id());
        assertThat(parser.parseYaml(a).nodes().get(0).id())
                .isNotEqualTo(parser.parseYaml(b).nodes().get(0).id());
    }
}
