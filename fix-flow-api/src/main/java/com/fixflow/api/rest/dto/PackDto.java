package com.fixflow.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fixflow.core.domain.pack.PackManifest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Wire forms of the pack endpoints. */
public final class PackDto {

    private PackDto() {}

    /** One installed pack in {@code GET /api/v1/packs}. */
    public record Summary(String id, String name, String version, String provider, String description,
                          Instant installedAt, Instant updatedAt, Counts counts) {}

    public record Counts(int scenarios, int datasets, int sessions, int dictionaries) {}

    /** A template value of {@code pack.yaml}. */
    public record Value(String name, String description, @JsonProperty("default") String defaultValue,
                       boolean required) {
        public static Value from(PackManifest.Value v) {
            return new Value(v.name(), v.description(), v.defaultValue(), v.required());
        }
    }

    /**
     * {@code POST /api/v1/packs/inspect}: the manifest of a zip, not installed. When a pack with the
     * same id is installed, its version and the values it was installed with come along so an
     * update form can start from them.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Inspection(String id, String name, String version, String provider, String engineMin,
                             String description, List<Value> values, String installedVersion,
                             Map<String, String> installedValues) {}
}
