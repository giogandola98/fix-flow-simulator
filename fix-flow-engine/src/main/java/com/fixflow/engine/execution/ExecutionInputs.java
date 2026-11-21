package com.fixflow.engine.execution;

import java.util.Map;
import java.util.UUID;

/**
 * What a caller supplies when starting a run: which data set to read, which row (or all of them)
 * and extra scenario variables. Every part is optional; {@link #none()} reproduces a plain run.
 */
public record ExecutionInputs(UUID datasetId, Integer rowIndex, boolean allRows, Map<String, String> vars) {

    public ExecutionInputs {
        vars = vars == null ? Map.of() : Map.copyOf(vars);
    }

    public static ExecutionInputs none() {
        return new ExecutionInputs(null, null, false, Map.of());
    }
}
