package com.fixflow.core.domain.pack;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The {@code pack.yaml} of a provider pack: identity, the engine version it needs and the
 * template values its session files use as {@code ${NAME}}.
 */
public record PackManifest(String id, String name, String version, String provider, String engineMin,
                           String description, List<Value> values) {

    public static final Pattern ID = Pattern.compile("[a-z0-9-]+");
    public static final Pattern VALUE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public PackManifest {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("pack.yaml: id '" + id + "' must match [a-z0-9-]+");
        }
        if (name == null || name.isBlank()) throw new IllegalArgumentException("pack.yaml: name is required");
        if (version == null || version.isBlank()) throw new IllegalArgumentException("pack.yaml: version is required");
        values = values == null ? List.of() : List.copyOf(values);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Value v : values) {
            if (!seen.add(v.name())) throw new IllegalArgumentException("pack.yaml: value '" + v.name() + "' declared twice");
        }
    }

    /** A template variable; without a default the import must supply it. */
    public record Value(String name, String description, String defaultValue) {
        public Value {
            if (name == null || !VALUE_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("pack.yaml: value name '" + name + "' must match [A-Za-z_][A-Za-z0-9_]*");
            }
        }

        public boolean required() { return defaultValue == null; }
    }
}
