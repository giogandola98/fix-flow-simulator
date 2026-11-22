package com.fixflow.api.rest.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Body of POST /api/v1/scenarios/{id}/execute. Every field is optional: {@code datasetId} and
 * {@code rowIndex} pick the data set row, {@code allRows} runs one execution per row, {@code vars}
 * sets scenario variables.
 */
public record StartExecutionRequest(UUID sessionId, UUID datasetId, Integer rowIndex, Boolean allRows,
                                    Map<String, String> vars) {}
