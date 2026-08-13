package com.fixflow.adapters.persistence;

import com.fixflow.adapters.persistence.jpa.JpaScenarioRepository;
import com.fixflow.core.domain.scenario.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ScenarioRepositoryAdapterTest {

    @Autowired ScenarioRepositoryAdapter adapter;
    @Autowired JpaScenarioRepository scenarioRepo;

    private Scenario scenario(UUID id, String name, String version) {
        ScenarioNode start = new ScenarioNode("n1", "Start", NodeType.START, Map.of(), null, null, "n2", null, null);
        ScenarioNode send = new ScenarioNode("n2", "Send", NodeType.SEND_FIX,
                Map.of("fields", Map.of("35", "D")), null, null, null, null, null);
        return new Scenario(id, name, "desc", version, "sess",
                RuntimePolicy.SEQUENTIAL, List.of(), List.of(),
                List.of(start, send), List.of(new ScenarioEdge("n1", "n2", "next")),
                Map.of(), null);
    }

    @Test
    void saveAndFindByIdRoundTripsViaYaml() {
        UUID id = UUID.randomUUID();
        adapter.save(scenario(id, "demo", "1.0"));

        Scenario loaded = adapter.findById(id).orElseThrow();
        assertThat(loaded.name()).isEqualTo("demo");
        assertThat(loaded.version()).isEqualTo("1.0");
        assertThat(loaded.runtimePolicy()).isEqualTo(RuntimePolicy.SEQUENTIAL);
        assertThat(loaded.nodes()).hasSize(2);
        assertThat(loaded.startNode()).isPresent();
        assertThat(loaded.findNode("n2").orElseThrow().type()).isEqualTo(NodeType.SEND_FIX);
    }

    @Test
    void findByIdEmptyWhenAbsent() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void saveIsUpsert() {
        UUID id = UUID.randomUUID();
        adapter.save(scenario(id, "first", "1.0"));
        adapter.save(scenario(id, "second", "2.0"));

        assertThat(scenarioRepo.count()).isEqualTo(1);
        assertThat(adapter.findById(id).orElseThrow().name()).isEqualTo("second");
    }

    @Test
    void savePrefersRawYamlWhenPresent() {
        UUID id = UUID.randomUUID();
        String raw = "id: " + id + "\nname: raw-name\nversion: 9.9\nnodes:\n" +
                "  - id: s\n    name: Start\n    type: START\n";
        Scenario s = new Scenario(id, "ignored-name", "d", "1.0", "sess",
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(new ScenarioNode("s", "Start", NodeType.START, Map.of(), null, null, null, null, null)),
                List.of(), Map.of(), raw);

        adapter.save(s);

        // Stored YAML is the raw string, so reload reflects raw-name / 9.9.
        Scenario loaded = adapter.findById(id).orElseThrow();
        assertThat(loaded.name()).isEqualTo("raw-name");
        assertThat(loaded.version()).isEqualTo("9.9");
    }

    @Test
    void findAllReturnsAllSavedScenarios() {
        adapter.save(scenario(UUID.randomUUID(), "a", "1.0"));
        adapter.save(scenario(UUID.randomUUID(), "b", "1.0"));

        List<Scenario> all = adapter.findAll();
        assertThat(all).hasSize(2);
        assertThat(all).extracting(Scenario::name).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void deleteRemovesScenario() {
        UUID id = UUID.randomUUID();
        adapter.save(scenario(id, "demo", "1.0"));
        adapter.delete(id);

        assertThat(adapter.findById(id)).isEmpty();
    }

    @Test
    void saveVersionAndFindVersionsReturnsAllStoredVersions() {
        UUID id = UUID.randomUUID();
        adapter.saveVersion(scenario(id, "demo", "1.0"));
        adapter.saveVersion(scenario(id, "demo", "1.1"));
        adapter.saveVersion(scenario(id, "demo", "1.2"));

        // Ordered by savedAt desc; rapid inserts can share a timestamp so assert set membership.
        List<String> versions = adapter.findVersions(id);
        assertThat(versions).containsExactlyInAnyOrder("1.2", "1.1", "1.0");
    }

    @Test
    void findVersionsEmptyWhenNone() {
        assertThat(adapter.findVersions(UUID.randomUUID())).isEmpty();
    }
}
