package com.fixflow.engine.scenario;

import com.fixflow.core.domain.scenario.Scenario;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScenarioRegistry {

    private final ConcurrentHashMap<UUID, Scenario> current = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Map<String, Scenario>> versions = new ConcurrentHashMap<>();

    public void register(Scenario scenario) {
        current.put(scenario.id(), scenario);
        String version = scenario.version() == null ? "1" : scenario.version();
        versions.computeIfAbsent(scenario.id(), k -> new ConcurrentHashMap<>())
                .put(version, scenario);
    }

    public void reload(Scenario newVersion) {
        register(newVersion);
    }

    public Optional<Scenario> getById(UUID id) {
        return Optional.ofNullable(current.get(id));
    }

    public Optional<Scenario> getVersion(UUID id, String version) {
        Map<String, Scenario> byVersion = versions.get(id);
        return byVersion == null ? Optional.empty() : Optional.ofNullable(byVersion.get(version));
    }

    public List<Scenario> findAll() {
        return List.copyOf(current.values());
    }

    public void unregister(UUID id) {
        current.remove(id);
        versions.remove(id);
    }
}
