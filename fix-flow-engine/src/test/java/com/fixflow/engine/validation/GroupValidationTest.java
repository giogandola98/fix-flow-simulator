package com.fixflow.engine.validation;

import com.fixflow.core.domain.execution.FIXMessageData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GroupValidationTest {

    private final ValidationEngine engine = new ValidationEngine(new DateRuleEngine());

    private FIXMessageData swap() {
        FIXMessageData near = FIXMessageData.ofFields(Map.of(600, "EUR/USD", 609, "FXSPOT"));
        FIXMessageData far  = FIXMessageData.ofFields(Map.of(600, "EUR/USD", 609, "FXFWD"));
        return new FIXMessageData(Map.of(35, "AB", 55, "EUR/USD"), Map.of(555, List.of(near, far)));
    }

    private ValidationRuleConfig rule(int tag, Integer groupTag, String index, String type, String value) {
        return new ValidationRuleConfig(tag, type, value, null, null, null, null, 0, groupTag, index);
    }

    private ValidationConfig cfg(ValidationRuleConfig... rules) {
        return new ValidationConfig(List.of(rules), Map.of(), false);
    }

    @Test
    void validatesEachLegByIndex() {
        ValidationSummary s = engine.validate(cfg(
                rule(609, 555, "0", "EQUALS", "FXSPOT"),
                rule(609, 555, "1", "EQUALS", "FXFWD")), swap(), null, Instant.now());
        assertTrue(s.passed());
    }

    @Test
    void failsWhenALegDoesNotMatch() {
        ValidationSummary s = engine.validate(cfg(
                rule(609, 555, "1", "EQUALS", "FXSPOT")), swap(), null, Instant.now());
        assertFalse(s.passed());
    }

    @Test
    void wildcardIndexAppliesToEveryEntry() {
        ValidationSummary pass = engine.validate(cfg(
                rule(600, 555, "*", "EQUALS", "EUR/USD")), swap(), null, Instant.now());
        assertTrue(pass.passed());

        ValidationSummary fail = engine.validate(cfg(
                rule(609, 555, "*", "EQUALS", "FXSPOT")), swap(), null, Instant.now());
        assertFalse(fail.passed(), "far leg is FXFWD, so a wildcard EQUALS FXSPOT must fail");
    }

    @Test
    void ruleWithoutGroupTagStillValidatesTopLevel() {
        ValidationSummary s = engine.validate(cfg(
                rule(55, null, null, "EQUALS", "EUR/USD")), swap(), null, Instant.now());
        assertTrue(s.passed());
    }

    @Test
    void missingGroupFailsRatherThanPassingVacuously() {
        ValidationSummary s = engine.validate(cfg(
                rule(600, 999, "0", "FIELD_PRESENT", null)), swap(), null, Instant.now());
        assertFalse(s.passed());
    }

    @Test
    void strictModeIgnoresGroupEntryFields() {
        ValidationConfig strict = new ValidationConfig(
                List.of(rule(35, null, null, "EQUALS", "AB"), rule(55, null, null, "EQUALS", "EUR/USD")),
                Map.of(), true);
        assertTrue(engine.validate(strict, swap(), null, Instant.now()).passed(),
                "strict mode checks top-level fields only; group content is structural");
    }
}
