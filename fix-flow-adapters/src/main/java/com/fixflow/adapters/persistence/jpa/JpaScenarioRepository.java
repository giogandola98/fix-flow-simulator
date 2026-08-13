package com.fixflow.adapters.persistence.jpa;

import com.fixflow.adapters.persistence.entity.ScenarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaScenarioRepository extends JpaRepository<ScenarioEntity, UUID> {
}
