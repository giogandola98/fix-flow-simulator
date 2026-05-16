package com.fixflow.core.ports.outbound;

import com.fixflow.core.domain.scenario.Scenario;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScenarioRepositoryPort {
    Scenario save(Scenario scenario);
    Optional<Scenario> findById(UUID id);
    List<Scenario> findAll();
    void delete(UUID id);
    void saveVersion(Scenario scenario);
    List<String> findVersions(UUID id);
}
