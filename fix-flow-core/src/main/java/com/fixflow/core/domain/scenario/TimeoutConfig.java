package com.fixflow.core.domain.scenario;

public record TimeoutConfig(long value, TimeUnit unit, TimeoutAction onTimeout, String jumpTo) {
    public TimeoutConfig {
        if (unit == null) throw new IllegalArgumentException("unit required");
        if (onTimeout == null) throw new IllegalArgumentException("onTimeout required");
        if (value < 0) throw new IllegalArgumentException("value must be >= 0");
        if (onTimeout == TimeoutAction.JUMP && (jumpTo == null || jumpTo.isBlank()))
            throw new IllegalArgumentException("jumpTo required when onTimeout=JUMP");
    }
    public long toMillis() {
        return switch (unit) {
            case MILLISECONDS -> value;
            case SECONDS -> Math.multiplyExact(value, 1_000L);
            case MINUTES -> Math.multiplyExact(value, 60_000L);
            case HOURS   -> Math.multiplyExact(value, 3_600_000L);
            case DAYS    -> Math.multiplyExact(value, 86_400_000L);
        };
    }
}
