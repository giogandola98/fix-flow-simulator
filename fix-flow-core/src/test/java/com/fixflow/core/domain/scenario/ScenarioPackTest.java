package com.fixflow.core.domain.scenario;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioPackTest {

    @Test
    void withIdAndPackKeepsContentAndReplacesIdentity() {
        Scenario s = new Scenario(UUID.randomUUID(), "s", "d", "1", "ref", null, null, null, null, null, null,
            "name: s", "rows");
        assertThat(s.packId()).isNull();
        UUID id = UUID.randomUUID();
        Scenario packed = s.withIdAndPack(id, "demo");
        assertThat(packed.id()).isEqualTo(id);
        assertThat(packed.packId()).isEqualTo("demo");
        assertThat(packed.datasetRef()).isEqualTo("rows");
        assertThat(packed.rawYaml()).isEqualTo("name: s");
        assertThat(packed.sessionRef()).isEqualTo("ref");
    }
}
