package com.fixflow.api.rest.dto;

import com.fixflow.core.domain.scenario.Scenario;
import java.util.UUID;

/** {@code packId} is the provider pack that installed the scenario, null otherwise. */
public record ScenarioDto(UUID id, String name, String description, String version, String sessionRef, int nodeCount,
                          String yamlDsl, String packId) {
    public static ScenarioDto from(Scenario s) {
        return new ScenarioDto(s.id(), s.name(), s.description(), s.version(), s.sessionRef(), s.nodes().size(), null,
            s.packId());
    }

    public static ScenarioDto withYaml(Scenario s, String yaml) {
        return new ScenarioDto(s.id(), s.name(), s.description(), s.version(), s.sessionRef(), s.nodes().size(), yaml,
            s.packId());
    }
}
