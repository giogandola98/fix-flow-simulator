package com.fixflow.engine.scenario;

import com.fixflow.core.domain.scenario.RuntimePolicy;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.engine.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.fixflow.engine.support.Fixtures.endPass;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static org.assertj.core.api.Assertions.assertThat;

class ScenarioRegistryTest {

    private ScenarioRegistry registry;

    @BeforeEach
    void setUp() { registry = new ScenarioRegistry(); }

    private Scenario scenarioV(UUID id, String version) {
        return new Scenario(id, "s", "", version, "sess",
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(start("end"), endPass("end")), List.of(), Map.of(), null);
    }

    @Test
    void registerAndGetById() {
        Scenario s = scenario(UUID.randomUUID(), "s", List.of(), start("end"), endPass("end"));
        registry.register(s);
        assertThat(registry.getById(s.id())).contains(s);
    }

    @Test
    void unknownIdIsEmpty() {
        assertThat(registry.getById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void nullVersionDefaultsToVersionOne() {
        UUID id = UUID.randomUUID();
        Scenario s = scenarioV(id, null);
        registry.register(s);
        assertThat(registry.getVersion(id, "1")).contains(s);
    }

    @Test
    void keepsMultipleVersions() {
        UUID id = UUID.randomUUID();
        Scenario v1 = scenarioV(id, "1");
        Scenario v2 = scenarioV(id, "2");
        registry.register(v1);
        registry.register(v2);
        assertThat(registry.getVersion(id, "1")).contains(v1);
        assertThat(registry.getVersion(id, "2")).contains(v2);
        assertThat(registry.getById(id)).contains(v2); // current = latest registered
    }

    @Test
    void getVersionUnknownIsEmpty() {
        assertThat(registry.getVersion(UUID.randomUUID(), "1")).isEmpty();
        UUID id = UUID.randomUUID();
        registry.register(scenarioV(id, "1"));
        assertThat(registry.getVersion(id, "99")).isEmpty();
    }

    @Test
    void reloadReplacesCurrent() {
        UUID id = UUID.randomUUID();
        Scenario v1 = scenarioV(id, "1");
        Scenario v2 = scenarioV(id, "2");
        registry.register(v1);
        registry.reload(v2);
        assertThat(registry.getById(id)).contains(v2);
    }

    @Test
    void findAllReturnsAllCurrent() {
        Scenario a = scenario(UUID.randomUUID(), "a", List.of(), start("end"), endPass("end"));
        Scenario b = scenario(UUID.randomUUID(), "b", List.of(), start("end"), endPass("end"));
        registry.register(a);
        registry.register(b);
        assertThat(registry.findAll()).containsExactlyInAnyOrder(a, b);
    }

    @Test
    void unregisterRemovesCurrentAndVersions() {
        UUID id = UUID.randomUUID();
        registry.register(scenarioV(id, "1"));
        registry.unregister(id);
        assertThat(registry.getById(id)).isEmpty();
        assertThat(registry.getVersion(id, "1")).isEmpty();
    }
}
