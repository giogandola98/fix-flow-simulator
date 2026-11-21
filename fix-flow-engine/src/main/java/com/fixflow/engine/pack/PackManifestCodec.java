package com.fixflow.engine.pack;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fixflow.core.domain.pack.PackManifest;

import java.util.List;
import java.util.Map;

/** {@code pack.yaml} to {@link PackManifest} and back, plus the YAML mapper the pack code shares. */
public final class PackManifestCodec {

    private PackManifestCodec() {}

    /** Block-style YAML, minimal quoting, nulls left out; unknown properties fail. */
    static ObjectMapper yamlMapper() {
        YAMLFactory yf = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        return new ObjectMapper(yf)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private static final ObjectMapper YAML = yamlMapper();

    @JsonPropertyOrder({"id", "name", "version", "provider", "engineMin", "description", "values"})
    record ManifestDto(String id, String name, String version, String provider, String engineMin,
                       String description, List<ValueDto> values) {}

    @JsonPropertyOrder({"name", "description", "default"})
    record ValueDto(String name, String description, @JsonProperty("default") String defaultValue) {}

    public static PackManifest parse(String yaml) {
        ManifestDto d;
        try {
            d = YAML.readValue(yaml, ManifestDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("pack.yaml: " + e.getOriginalMessage().split("\\R", 2)[0], e);
        }
        if (d == null) throw new IllegalArgumentException("pack.yaml is empty");
        List<PackManifest.Value> values = d.values() == null ? List.of() : d.values().stream()
                .map(v -> new PackManifest.Value(v.name(), v.description(), v.defaultValue()))
                .toList();
        return new PackManifest(d.id(), d.name(), d.version(), d.provider(), d.engineMin(), d.description(), values);
    }

    /** Writes {@code m}; a value in {@code defaults} becomes that value's {@code default}. */
    public static String write(PackManifest m, Map<String, String> defaults) {
        List<ValueDto> values = m.values().stream()
                .map(v -> new ValueDto(v.name(), v.description(),
                        defaults.getOrDefault(v.name(), v.defaultValue())))
                .toList();
        try {
            return YAML.writeValueAsString(new ManifestDto(m.id(), m.name(), m.version(), m.provider(),
                    m.engineMin(), m.description(), values.isEmpty() ? null : values));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot write pack.yaml", e);
        }
    }
}
