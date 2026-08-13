package com.fixflow.engine.scenario;

import com.fixflow.core.domain.scenario.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioDslParserTest {

    private ScenarioDslParser parser;

    @BeforeEach
    void setUp() { parser = new ScenarioDslParser(); }

    private Scenario sample(UUID id) {
        ScenarioNode start = new ScenarioNode("start", "Start", NodeType.START, Map.of(),
                null, null, "expect", null, null);
        ScenarioNode expect = new ScenarioNode("expect", "Expect", NodeType.EXPECT_FIX,
                Map.of("msgType", "8"),
                new TimeoutConfig(5, TimeUnit.SECONDS, TimeoutAction.JUMP, "recover"),
                null, "end", "fail", null);
        ScenarioNode recover = new ScenarioNode("recover", "Recover", NodeType.END_FAIL, Map.of(),
                null, null, null, null, null);
        ScenarioNode end = new ScenarioNode("end", "End", NodeType.END_PASS, Map.of(),
                null, null, null, null, null);
        ScenarioNode fail = new ScenarioNode("fail", "Fail", NodeType.END_FAIL, Map.of(),
                null, null, null, null, null);
        return new Scenario(id, "Round Trip", "desc", "1", "sess",
                RuntimePolicy.SEQUENTIAL, List.of(), List.of(),
                List.of(start, expect, recover, end, fail),
                List.of(new ScenarioEdge("start", "expect", "go")), Map.of(), null);
    }

    @Test
    void roundTripPreservesStructure() {
        UUID id = UUID.randomUUID();
        Scenario original = sample(id);
        String yaml = parser.toYaml(original);
        Scenario parsed = parser.parseYaml(yaml);

        assertThat(parsed.id()).isEqualTo(id);
        assertThat(parsed.name()).isEqualTo("Round Trip");
        assertThat(parsed.version()).isEqualTo("1");
        assertThat(parsed.runtimePolicy()).isEqualTo(RuntimePolicy.SEQUENTIAL);
        assertThat(parsed.nodes()).extracting(ScenarioNode::id)
                .containsExactly("start", "expect", "recover", "end", "fail");
        assertThat(parsed.findNode("start").get().onSuccess()).isEqualTo("expect");
        assertThat(parsed.edges()).hasSize(1);
    }

    @Test
    void roundTripPreservesTimeoutJumpTo() {
        Scenario parsed = parser.parseYaml(parser.toYaml(sample(UUID.randomUUID())));
        TimeoutConfig t = parsed.findNode("expect").get().timeout();
        assertThat(t).isNotNull();
        assertThat(t.onTimeout()).isEqualTo(TimeoutAction.JUMP);
        assertThat(t.jumpTo()).isEqualTo("recover");
        assertThat(t.unit()).isEqualTo(TimeUnit.SECONDS);
        assertThat(t.value()).isEqualTo(5);
    }

    @Test
    void parsesMinimalYamlAndGeneratesIdWhenOmitted() {
        String yaml = """
                name: Minimal
                nodes:
                  - id: start
                    name: Start
                    type: START
                    onSuccess: end
                  - id: end
                    name: End
                    type: END_PASS
                """;
        Scenario parsed = parser.parseYaml(yaml);
        assertThat(parsed.id()).isNotNull();
        assertThat(parsed.name()).isEqualTo("Minimal");
        assertThat(parsed.runtimePolicy()).isEqualTo(RuntimePolicy.PARALLEL); // default
        assertThat(parsed.startNode()).isPresent();
        assertThat(parsed.rawYaml()).isEqualTo(yaml);
    }

    @Test
    void ignoresUnknownProperties() {
        String yaml = """
                name: WithExtras
                unknownField: whatever
                nodes:
                  - id: start
                    name: Start
                    type: START
                """;
        Scenario parsed = parser.parseYaml(yaml);
        assertThat(parsed.name()).isEqualTo("WithExtras");
    }

    @Test
    void invalidYamlThrows() {
        assertThatThrownBy(() -> parser.parseYaml("\t not: [valid"))
                .isInstanceOf(RuntimeException.class);
    }
}
