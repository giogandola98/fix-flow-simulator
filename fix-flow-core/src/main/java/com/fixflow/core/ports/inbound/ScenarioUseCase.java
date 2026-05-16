package com.fixflow.core.ports.inbound;

import com.fixflow.core.domain.scenario.Scenario;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScenarioUseCase {
    Scenario save(Scenario scenario);
    Optional<Scenario> findById(UUID id);
    List<Scenario> findAll();
    void delete(UUID id);
    List<String> getVersions(UUID id);
}
