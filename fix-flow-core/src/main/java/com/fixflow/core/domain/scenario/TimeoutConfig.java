package com.fixflow.core.domain.scenario;

public record TimeoutConfig(long value, TimeUnit unit, TimeoutAction onTimeout, String jumpTo) {
    public long toMillis() {
        return switch (unit) {
            case MILLISECONDS -> value;
            case SECONDS -> value * 1_000L;
            case MINUTES -> value * 60_000L;
            case HOURS   -> value * 3_600_000L;
            case DAYS    -> value * 86_400_000L;
        };
    }
}
