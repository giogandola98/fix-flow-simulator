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

class ValidationEngineTest {

    private final ValidationEngine engine = new ValidationEngine(new DateRuleEngine());

    private static ExecutionContext freshCtx() {
        Scenario s = new Scenario(UUID.randomUUID(), "t", "", "1.0", "s",
                RuntimePolicy.PARALLEL, List.of(), List.of(), List.of(), List.of(), Map.of());
        return new ExecutionContext(UUID.randomUUID(), s, UUID.randomUUID());
    }

    @Test
    void passesWhenAllRulesPass() {
        ValidationConfig cfg = new ValidationConfig(
            List.of(
                new ValidationRuleConfig(35, "EQUALS", "S", null, null, null, null, 0),
                new ValidationRuleConfig(131, "FIELD_PRESENT", null, null, null, null, null, 0)
            ),
            Map.of(),
            false
        );
        Map<Integer, String> fields = Map.of(35, "S", 131, "QR-1");
        ValidationSummary s = engine.validate(cfg, fields, freshCtx(), Instant.now());
        assertThat(s.passed()).isTrue();
    }

    @Test
    void failsInStrictModeWhenUnexpectedTagPresent() {
        ValidationConfig cfg = new ValidationConfig(
            List.of(
                new ValidationRuleConfig(35, "EQUALS", "S", null, null, null, null, 0),
                new ValidationRuleConfig(131, "FIELD_PRESENT", null, null, null, null, null, 0)
            ),
            Map.of(),
            true
        );
        Map<Integer, String> fields = Map.of(35, "S", 131, "QR-1", 999, "EXTRA");
        ValidationSummary s = engine.validate(cfg, fields, freshCtx(), Instant.now());
        assertThat(s.passed()).isFalse();
        assertThat(s.results()).anyMatch(r -> !r.passed() && r.tag() == 999);
    }

    @Test
    void passesInNonStrictModeWhenExtraTagPresent() {
        ValidationConfig cfg = new ValidationConfig(
            List.of(
                new ValidationRuleConfig(35, "EQUALS", "S", null, null, null, null, 0)
            ),
            Map.of(),
            false
        );
        Map<Integer, String> fields = Map.of(35, "S", 999, "EXTRA");
        ValidationSummary s = engine.validate(cfg, fields, freshCtx(), Instant.now());
        assertThat(s.passed()).isTrue();
    }

    @Test
    void appliesDateRuleFromConfig() {
        ExecutionContext ctx = freshCtx();
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        ctx.storeNodeMessage("n1", Map.of(60, base.toString()));
        DateRule fo = new DateRule("fo1", DateRuleType.FIELD_OFFSET, "n1", 60, 5, TimeUnit.MINUTES, 1, TimeUnit.SECONDS);
        ValidationConfig cfg = new ValidationConfig(
            List.of(new ValidationRuleConfig(126, "DATE_RULE", null, null, null, "fo1", null, 0)),
            Map.of("fo1", fo),
            false
        );
        Map<Integer, String> fields = Map.of(126, base.plus(300, ChronoUnit.SECONDS).toString());
        ValidationSummary s = engine.validate(cfg, fields, ctx, Instant.now());
        assertThat(s.passed()).isTrue();
    }
}
