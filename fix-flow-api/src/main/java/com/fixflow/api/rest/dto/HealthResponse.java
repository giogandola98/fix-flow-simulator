package com.fixflow.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Body of {@code GET /api/v1/system/health}. {@code status} is {@code "UP"} or {@code "DOWN"};
 * {@code reason} is present only when down.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HealthResponse(String status, String database, String reason, Instant timestamp) {

    public static HealthResponse up() {
        return new HealthResponse("UP", "UP", null, Instant.now());
    }

    public static HealthResponse down(String reason) {
        return new HealthResponse("DOWN", "DOWN", reason, Instant.now());
    }
}
