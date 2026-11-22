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
        e.setYamlDsl(scenario.rawYaml() != null ? scenario.rawYaml() : parser.toYaml(scenario));
        // The pack tag lives on the row, not in the YAML: a save without one (an edit through the
        // scenario API) keeps the row's tag, so an edited pack scenario stays part of its pack.
        if (scenario.packId() != null) e.setPackId(scenario.packId());
        scenarioRepo.save(e);
        return e.getPackId() == null || e.getPackId().equals(scenario.packId())
                ? scenario : scenario.withIdAndPack(scenario.id(), e.getPackId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Scenario> findById(UUID id) {
        return scenarioRepo.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Scenario> findAll() {
        return scenarioRepo.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    /**
     * Reads a row back, keeping the ROW's id as the scenario's identity.
     *
     * <p>The content is stored as raw YAML and re-parsed here, and {@code ScenarioDslParser} mints
     * a fresh UUID for a document that carries no {@code id:} — which the graphical editor always
     * writes but hand-authored or imported YAML often does not. Trusting the document therefore
     * handed out a different identity on every read, unrelated to the row it came from, so the
     * scenario could not be fetched, updated, executed or deleted by the id the API had just
     * listed (issue #94). The row id is the only stable one; it wins. The pack tag also comes
     * from the row.
     */
    private Scenario toDomain(ScenarioEntity e) {
        Scenario parsed = parser.parseYaml(e.getYamlDsl());
        return parsed.withIdAndPack(e.getId() != null ? e.getId() : parsed.id(), e.getPackId());
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
