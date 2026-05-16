package com.fixflow.adapters.persistence.jpa;

import com.fixflow.adapters.persistence.entity.ScenarioVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaScenarioVersionRepository extends JpaRepository<ScenarioVersionEntity, UUID> {
    List<ScenarioVersionEntity> findByScenarioIdOrderBySavedAtDesc(UUID scenarioId);
}
