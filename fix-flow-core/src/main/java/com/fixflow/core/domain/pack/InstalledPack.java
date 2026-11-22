package com.fixflow.core.domain.pack;

import java.time.Instant;
import java.util.Map;

/**
 * A pack as installed: its manifest, the template values the import ran with (defaults
 * included) and when it was first installed and last updated.
 */
public record InstalledPack(PackManifest manifest, Map<String, String> values, Instant installedAt,
                            Instant updatedAt) {
    public InstalledPack {
        if (manifest == null) throw new IllegalArgumentException("manifest required");
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public String id() { return manifest.id(); }
}
