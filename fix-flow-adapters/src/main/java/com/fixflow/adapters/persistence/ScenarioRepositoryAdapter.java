package com.fixflow.adapters.persistence;

import com.fixflow.adapters.persistence.entity.ScenarioEntity;
import com.fixflow.adapters.persistence.entity.ScenarioVersionEntity;
import com.fixflow.adapters.persistence.jpa.JpaScenarioRepository;
import com.fixflow.adapters.persistence.jpa.JpaScenarioVersionRepository;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.ports.outbound.ScenarioRepositoryPort;
import com.fixflow.engine.scenario.ScenarioDslParser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ScenarioRepositoryAdapter implements ScenarioRepositoryPort {

    private final JpaScenarioRepository scenarioRepo;
    private final JpaScenarioVersionRepository versionRepo;
    private final ScenarioDslParser parser;

    public ScenarioRepositoryAdapter(JpaScenarioRepository scenarioRepo,
                                     JpaScenarioVersionRepository versionRepo,
                                     ScenarioDslParser parser) {
        this.scenarioRepo = scenarioRepo;
        this.versionRepo = versionRepo;
        this.parser = parser;
    }

    @Override
    @Transactional
    public Scenario save(Scenario scenario) {
        ScenarioEntity e = scenarioRepo.findById(scenario.id()).orElseGet(ScenarioEntity::new);
        e.setId(scenario.id());
        e.setName(scenario.name());
        e.setVersion(scenario.version());
        e.setYamlDsl(parser.toYaml(scenario));
        scenarioRepo.save(e);
        return scenario;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Scenario> findById(UUID id) {
        return scenarioRepo.findById(id).map(e -> parser.parseYaml(e.getYamlDsl()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Scenario> findAll() {
        return scenarioRepo.findAll().stream()
                .map(e -> parser.parseYaml(e.getYamlDsl()))
                .toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) { scenarioRepo.deleteById(id); }

    @Override
    @Transactional
    public void saveVersion(Scenario scenario) {
        ScenarioVersionEntity v = new ScenarioVersionEntity();
        v.setScenarioId(scenario.id());
        v.setVersion(scenario.version());
        v.setYamlDsl(parser.toYaml(scenario));
        versionRepo.save(v);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findVersions(UUID id) {
        return versionRepo.findByScenarioIdOrderBySavedAtDesc(id).stream()
                .map(ScenarioVersionEntity::getVersion)
                .toList();
    }
}
