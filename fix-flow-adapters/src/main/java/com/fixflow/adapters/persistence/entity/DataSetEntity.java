package com.fixflow.adapters.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "data_sets", uniqueConstraints = @UniqueConstraint(name = "uk_data_sets_name", columnNames = "name"))
public class DataSetEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String packId;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String columnsJson;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String rowsJson;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onInsert() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPackId() { return packId; }
    public void setPackId(String packId) { this.packId = packId; }
    public String getColumnsJson() { return columnsJson; }
    public void setColumnsJson(String columnsJson) { this.columnsJson = columnsJson; }
    public String getRowsJson() { return rowsJson; }
    public void setRowsJson(String rowsJson) { this.rowsJson = rowsJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
