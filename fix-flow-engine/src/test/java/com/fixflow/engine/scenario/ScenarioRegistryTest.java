package com.fixflow.engine.scenario;

import com.fixflow.core.domain.scenario.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioRegistryTest {

    private Scenario scenario(UUID id, String version) {
        return new Scenario(id, "demo", "", version, "sess",
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(new ScenarioNode("n1", "s", NodeType.START, Map.of(), null, null, null, null, null)),
                List.of(), Map.of());
    }

    @Test
    void registerAndRetrieve() {
        ScenarioRegistry reg = new ScenarioRegistry();
        UUID id = UUID.randomUUID();
        Scenario s1 = scenario(id, "1.0");

        reg.register(s1);

        assertThat(reg.getById(id)).contains(s1);
        assertThat(reg.findAll()).containsExactly(s1);
    }

    @Test
    void reloadKeepsHistoricVersionsAccessible() {
        ScenarioRegistry reg = new ScenarioRegistry();
        UUID id = UUID.randomUUID();
        Scenario v1 = scenario(id, "1.0");
        Scenario v2 = scenario(id, "2.0");

        reg.register(v1);
        reg.reload(v2);

        assertThat(reg.getById(id)).contains(v2);
        assertThat(reg.getVersion(id, "1.0")).contains(v1);
        assertThat(reg.getVersion(id, "2.0")).contains(v2);
    }

    @Test
    void unknownIdReturnsEmpty() {
        ScenarioRegistry reg = new ScenarioRegistry();
        assertThat(reg.getById(UUID.randomUUID())).isEmpty();
    }
}
