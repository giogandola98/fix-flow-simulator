package com.fixflow.engine.pack;

import java.util.Map;

/**
 * The files of a pack, read but not yet interpreted. Keys are file names inside their folder
 * ({@code instruments.csv}, not {@code datasets/instruments.csv}), sorted.
 */
public record PackContents(String manifestYaml,
                           Map<String, byte[]> dictionaries,
                           Map<String, String> datasets,
                           Map<String, String> scenarios,
                           Map<String, String> sessions) {
}
