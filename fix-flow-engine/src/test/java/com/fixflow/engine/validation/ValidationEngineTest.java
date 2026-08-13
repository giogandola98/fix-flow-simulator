package com.fixflow.engine.validation;

import com.fixflow.engine.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationEngineTest {

    private ValidationEngine engine;

    @BeforeEach
    void setUp() { engine = new ValidationEngine(new DateRuleEngine()); }

    private ValidationRuleConfig rc(int tag, String rule, String value) {
        return new ValidationRuleConfig(tag, rule, value, null, null, null, null, 0);
    }

    private ValidationConfig config(boolean strict, ValidationRuleConfig... rules) {
        return new ValidationConfig(List.of(rules), Map.of(), strict);
    }

    @Test
    void allRulesPassProducesPassedSummary() {
        ValidationSummary s = engine.validate(config(false, rc(35, "EQUALS", "8")),
                Fixtures.fields(35, "8"), null, Instant.now());
        assertThat(s.passed()).isTrue();
        assertThat(s.results()).hasSize(1);
    }

    @Test
    void anyRuleFailingProducesFailedSummary() {
        ValidationSummary s = engine.validate(config(false, rc(35, "EQUALS", "8"), rc(44, "EQUALS", "10")),
                Fixtures.fields(35, "8", 44, "99"), null, Instant.now());
        assertThat(s.passed()).isFalse();
    }

    @Test
    void strictModeFailsOnUnexpectedNonHeaderField() {
        ValidationSummary s = engine.validate(config(true, rc(35, "EQUALS", "8")),
                Fixtures.fields(35, "8", 44, "extra"), null, Instant.now());
        assertThat(s.passed()).isFalse();
        assertThat(s.results()).anyMatch(r -> !r.passed() && r.ruleName().equals("STRICT"));
    }

    @Test
    void strictModeIgnoresHeaderTags() {
        // header tags 8,9,10,34,35,49,52,56 must not trip strict mode
        ValidationSummary s = engine.validate(config(true, rc(35, "EQUALS", "8")),
                Fixtures.fields(35, "8", 8, "FIX.4.4", 49, "SENDER", 56, "TARGET"), null, Instant.now());
        assertThat(s.passed()).isTrue();
    }

    @Test
    void dispatchesDateRuleToDateRuleEngine() {
        DateRule dr = new DateRule("d1", DateRuleType.CURRENT_TIMESTAMP, null, 0, 0,
                java.util.concurrent.TimeUnit.SECONDS, 5, java.util.concurrent.TimeUnit.SECONDS);
        ValidationRuleConfig rule = new ValidationRuleConfig(52, "DATE_RULE", null, null, null, "d1", null, 0);
        ValidationConfig cfg = new ValidationConfig(List.of(rule), Map.of("d1", dr), false);
        Instant now = Instant.now();
        ValidationSummary s = engine.validate(cfg, Fixtures.fields(52, now.toString()), null, now);
        assertThat(s.passed()).isTrue();
    }

    @Test
    void unknownRuleTypeThrows() {
        assertThatThrownBy(() -> engine.validate(config(false, rc(35, "NOPE", "x")),
                Fixtures.fields(35, "8"), null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownDateRuleIdThrows() {
        ValidationRuleConfig rule = new ValidationRuleConfig(52, "DATE_RULE", null, null, null, "ghost", null, 0);
        ValidationConfig cfg = new ValidationConfig(List.of(rule), Map.of(), false);
        assertThatThrownBy(() -> engine.validate(cfg, Fixtures.fields(52, Instant.now().toString()), null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyRuleSetPasses() {
        assertThat(engine.validate(config(false), Fixtures.fields(35, "8"), null, Instant.now()).passed()).isTrue();
    }
}
