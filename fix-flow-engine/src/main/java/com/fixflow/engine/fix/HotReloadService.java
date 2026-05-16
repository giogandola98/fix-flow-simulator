package com.fixflow.engine.fix;

import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.ports.outbound.ScenarioRepositoryPort;
import com.fixflow.engine.scenario.ScenarioRegistry;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class HotReloadService {

    private final ScenarioRegistry registry;
    private final MessageBuffer buffer;
    private final ScenarioRepositoryPort scenarioRepo;

    public HotReloadService(ScenarioRegistry registry, MessageBuffer buffer, ScenarioRepositoryPort scenarioRepo) {
        this.registry = registry;
        this.buffer = buffer;
        this.scenarioRepo = scenarioRepo;
    }

    public void reload(UUID scenarioId) {
        buffer.pause();
        try {
            Scenario latest = scenarioRepo.findById(scenarioId)
                .orElseThrow(() -> new IllegalArgumentException("scenario not found: " + scenarioId));
            registry.reload(latest);
        } finally {
            buffer.resume();
        }
    }
}
