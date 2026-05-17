package com.fixflow.api.config;

import com.fixflow.core.ports.outbound.ScenarioRepositoryPort;
import com.fixflow.engine.scenario.ScenarioRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ScenarioRegistryInitializer {

    private static final Logger log = LoggerFactory.getLogger(ScenarioRegistryInitializer.class);

    private final ScenarioRepositoryPort repo;
    private final ScenarioRegistry registry;

    public ScenarioRegistryInitializer(ScenarioRepositoryPort repo, ScenarioRegistry registry) {
        this.repo = repo;
        this.registry = registry;
    }

    @PostConstruct
    void populate() {
        var scenarios = repo.findAll();
        scenarios.forEach(registry::register);
        log.info("Registered {} scenario(s) from DB into engine registry", scenarios.size());
    }
}
