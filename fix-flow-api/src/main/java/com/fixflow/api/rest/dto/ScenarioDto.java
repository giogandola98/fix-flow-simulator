package com.fixflow.api.rest.dto;

import com.fixflow.core.domain.scenario.Scenario;
import java.util.UUID;

public record ScenarioDto(UUID id, String name, String description, String version, String sessionRef, int nodeCount, String yamlDsl) {
    public static ScenarioDto from(Scenario s) {
        return new ScenarioDto(s.id(), s.name(), s.description(), s.version(), s.sessionRef(), s.nodes().size(), null);
    }

    public static ScenarioDto withYaml(Scenario s, String yaml) {
        return new ScenarioDto(s.id(), s.name(), s.description(), s.version(), s.sessionRef(), s.nodes().size(), yaml);
    }
}
