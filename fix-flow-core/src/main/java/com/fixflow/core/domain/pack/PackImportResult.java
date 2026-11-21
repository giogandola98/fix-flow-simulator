package com.fixflow.core.domain.pack;

import java.util.List;

/** What a pack import created or updated, with non-fatal warnings. */
public record PackImportResult(String packId, String version, int scenarios, int datasets, int sessions,
                               int dictionaries, List<String> warnings) {
    public PackImportResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
