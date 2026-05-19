package com.fixflow.engine.validation;

import com.fixflow.core.domain.scenario.RuntimePolicy;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.engine.execution.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DateRuleEngineTest {

    private final DateRuleEngine engine = new DateRuleEngine();

    private static ExecutionContext freshCtx() {
        Scenario s = new Scenario(UUID.randomUUID(), "t", "", "1.0", "s",
                RuntimePolicy.PARALLEL, List.of(), List.of(), List.of(), List.of(), Map.of(), null);
        return new ExecutionContext(UUID.randomUUID(), s, UUID.randomUUID());
    }

    @Test
    void currentTimestampPassesWhenWithinTolerance() {
        Instant now = Instant.now();
        DateRule rule = new DateRule("ct", DateRuleType.CURRENT_TIMESTAMP,
                null, 0, 0, TimeUnit.SECONDS, 4, TimeUnit.SECONDS);
        Map<Integer, String> fields = Map.of(60, now.toString());
        ValidationResult r = engine.validate(rule, 60, fields, freshCtx(), now);
        assertThat(r.passed()).isTrue();
    }

    @Test
    void currentTimestampFailsWhenOutsideTolerance() {
        Instant now = Instant.now();
        Instant tenMinAgo = now.minus(10, ChronoUnit.MINUTES);
        DateRule rule = new DateRule("ct", DateRuleType.CURRENT_TIMESTAMP,
                null, 0, 0, TimeUnit.SECONDS, 4, TimeUnit.SECONDS);
        Map<Integer, String> fields = Map.of(60, tenMinAgo.toString());
        ValidationResult r = engine.validate(rule, 60, fields, freshCtx(), now);
        assertThat(r.passed()).isFalse();
    }

    @Test
    void fieldOffsetPassesWhenOffsetMatches() {
        ExecutionContext ctx = freshCtx();
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        ctx.storeNodeMessage("n1", Map.of(60, base.toString()));
        Instant target = base.plus(5, ChronoUnit.MINUTES);
        DateRule rule = new DateRule("fo", DateRuleType.FIELD_OFFSET,
                "n1", 60, 5, TimeUnit.MINUTES, 1, TimeUnit.SECONDS);
        Map<Integer, String> fields = Map.of(126, target.toString());
        ValidationResult r = engine.validate(rule, 126, fields, ctx, Instant.now());
        assertThat(r.passed()).isTrue();
    }

    @Test
    void fieldOffsetFailsWhenOffsetTooLarge() {
        ExecutionContext ctx = freshCtx();
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        ctx.storeNodeMessage("n1", Map.of(60, base.toString()));
        Instant target = base.plus(10, ChronoUnit.MINUTES);
        DateRule rule = new DateRule("fo", DateRuleType.FIELD_OFFSET,
                "n1", 60, 5, TimeUnit.MINUTES, 1, TimeUnit.SECONDS);
        Map<Integer, String> fields = Map.of(126, target.toString());
        ValidationResult r = engine.validate(rule, 126, fields, ctx, Instant.now());
        assertThat(r.passed()).isFalse();
    }
}
