package com.fixflow.adapters.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scenario_versions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"scenario_id", "version"}))
public class ScenarioVersionEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID scenarioId;

    @Column(nullable = false)
    private String version;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String yamlDsl;

    @Column(nullable = false)
    private Instant savedAt;

    @PrePersist
    void onInsert() { savedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getScenarioId() { return scenarioId; }
    public void setScenarioId(UUID scenarioId) { this.scenarioId = scenarioId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getYamlDsl() { return yamlDsl; }
    public void setYamlDsl(String yamlDsl) { this.yamlDsl = yamlDsl; }
    public Instant getSavedAt() { return savedAt; }
}
