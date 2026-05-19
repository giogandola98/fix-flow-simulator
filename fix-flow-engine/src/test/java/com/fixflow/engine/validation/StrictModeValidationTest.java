package com.fixflow.engine.validation;

import com.fixflow.core.domain.scenario.RuntimePolicy;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.engine.execution.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests specifically focused on strict-mode behaviour of {@link ValidationEngine}.
 */
class StrictModeValidationTest {

    private final ValidationEngine engine = new ValidationEngine(new DateRuleEngine());

    private static ExecutionContext freshCtx() {
        Scenario s = new Scenario(UUID.randomUUID(), "strict-test", "", "1.0", "sess",
                RuntimePolicy.PARALLEL, List.of(), List.of(), List.of(), List.of(), Map.of(), null);
        return new ExecutionContext(UUID.randomUUID(), s, UUID.randomUUID());
    }

    // --- Strict mode ON ---

    @Test
    void strictMode_failsWhenUnexpectedTagPresent() {
        ValidationConfig cfg = new ValidationConfig(
            List.of(new ValidationRuleConfig(35, "EQUALS", "D", null, null, null, null, 0)),
            Map.of(),
            true
        );
        Map<Integer, String> fields = Map.of(35, "D", 999, "UNEXPECTED");
        ValidationSummary summary = engine.validate(cfg, fields, freshCtx(), Instant.now());

        assertThat(summary.passed()).isFalse();
        assertThat(summary.results())
            .anyMatch(r -> !r.passed() && r.tag() == 999 && "STRICT".equals(r.ruleName()));
    }

    @Test
    void strictMode_passesWhenNoUnexpectedTags() {
        ValidationConfig cfg = new ValidationConfig(
            List.of(
                new ValidationRuleConfig(35, "EQUALS", "D", null, null, null, null, 0),
                new ValidationRuleConfig(11, "FIELD_PRESENT", null, null, null, null, null, 0)
            ),
            Map.of(),
            true
        );
        Map<Integer, String> fields = Map.of(35, "D", 11, "CL-1");
        ValidationSummary summary = engine.validate(cfg, fields, freshCtx(), Instant.now());

        assertThat(summary.passed()).isTrue();
        assertThat(summary.results()).allMatch(ValidationResult::passed);
    }

    @Test
    void strictMode_headerTagsAreExempted() {
        // Tags 8, 9, 10, 34, 35, 49, 52, 56 are always allowed in strict mode
        ValidationConfig cfg = new ValidationConfig(
            List.of(new ValidationRuleConfig(35, "EQUALS", "0", null, null, null, null, 0)),
            Map.of(),
            true
        );
        // Include several header tags alongside the declared tag
        Map<Integer, String> fields = Map.of(
            8, "FIX.4.4",
            9, "100",
            10, "123",
            34, "42",
            35, "0",
            49, "SENDER",
            52, "20260101-12:00:00",
            56, "TARGET"
        );
        ValidationSummary summary = engine.validate(cfg, fields, freshCtx(), Instant.now());

        assertThat(summary.passed()).isTrue();
        assertThat(summary.results()).noneMatch(r -> !r.passed() && "STRICT".equals(r.ruleName()));
    }

    @Test
    void strictMode_multipleUnexpectedTagsAllFail() {
        ValidationConfig cfg = new ValidationConfig(
            List.of(new ValidationRuleConfig(35, "EQUALS", "D", null, null, null, null, 0)),
            Map.of(),
            true
        );
        Map<Integer, String> fields = Map.of(35, "D", 100, "A", 200, "B");
        ValidationSummary summary = engine.validate(cfg, fields, freshCtx(), Instant.now());

        assertThat(summary.passed()).isFalse();
        long strictFailures = summary.results().stream()
            .filter(r -> !r.passed() && "STRICT".equals(r.ruleName()))
            .count();
        assertThat(strictFailures).isEqualTo(2);
    }

    // --- Strict mode OFF ---

    @Test
    void nonStrictMode_extraTagsAreIgnored() {
        ValidationConfig cfg = new ValidationConfig(
            List.of(new ValidationRuleConfig(35, "EQUALS", "D", null, null, null, null, 0)),
            Map.of(),
            false
        );
        Map<Integer, String> fields = Map.of(35, "D", 999, "IGNORED");
        ValidationSummary summary = engine.validate(cfg, fields, freshCtx(), Instant.now());

        assertThat(summary.passed()).isTrue();
        assertThat(summary.results()).noneMatch(r -> "STRICT".equals(r.ruleName()));
    }

    @Test
    void nonStrictMode_ruleFailurePropagates() {
        ValidationConfig cfg = new ValidationConfig(
            List.of(new ValidationRuleConfig(35, "EQUALS", "D", null, null, null, null, 0)),
            Map.of(),
            false
        );
        // Wrong value for tag 35
        Map<Integer, String> fields = Map.of(35, "8");
        ValidationSummary summary = engine.validate(cfg, fields, freshCtx(), Instant.now());

        assertThat(summary.passed()).isFalse();
        assertThat(summary.results())
            .anyMatch(r -> !r.passed() && r.tag() == 35);
    }

    // --- ENUM rule in strict mode ---

    @Test
    void strictMode_withEnumRule_rejectsUnknownValues() {
        ValidationConfig cfg = new ValidationConfig(
            List.of(new ValidationRuleConfig(40, "ENUM", null, List.of("1", "2"), null, null, null, 0)),
            Map.of(),
            true
        );
        // tag 40 is declared, but we also add tag 999
        Map<Integer, String> fields = Map.of(40, "3", 999, "EXTRA");
        ValidationSummary summary = engine.validate(cfg, fields, freshCtx(), Instant.now());

        assertThat(summary.passed()).isFalse();
        // ENUM failure on tag 40
        assertThat(summary.results()).anyMatch(r -> !r.passed() && r.tag() == 40);
        // STRICT failure on tag 999
        assertThat(summary.results()).anyMatch(r -> !r.passed() && r.tag() == 999 && "STRICT".equals(r.ruleName()));
    }
}
