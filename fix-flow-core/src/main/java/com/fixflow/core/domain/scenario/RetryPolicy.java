package com.fixflow.core.domain.scenario;

public record RetryPolicy(int maxAttempts, long delayMs) {
    public RetryPolicy {
        if (maxAttempts < 0) throw new IllegalArgumentException("maxAttempts must be >= 0");
        if (delayMs < 0) throw new IllegalArgumentException("delayMs must be >= 0");
    }
}
