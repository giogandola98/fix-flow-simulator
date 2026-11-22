package com.fixflow.adapters.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** An installed provider pack: its manifest as written and the values it was installed with. */
@Entity
@Table(name = "packs")
public class PackEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String version;

    private String provider;

    @Lob
    @Column(columnDefinition = "CLOB", nullable = false)
    private String manifestYaml;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String valuesJson;

    @Column(nullable = false)
    private Instant installedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getManifestYaml() { return manifestYaml; }
    public void setManifestYaml(String manifestYaml) { this.manifestYaml = manifestYaml; }
    public String getValuesJson() { return valuesJson; }
    public void setValuesJson(String valuesJson) { this.valuesJson = valuesJson; }
    public Instant getInstalledAt() { return installedAt; }
    public void setInstalledAt(Instant installedAt) { this.installedAt = installedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
