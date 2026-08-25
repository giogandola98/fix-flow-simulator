package com.fixflow.engine.validation;

import com.fixflow.engine.support.Fixtures;
import com.fixflow.engine.validation.rules.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationRulesTest {

    private static final Map<Integer, String> FIELDS = Fixtures.fields(35, "8", 44, "10.5", 55, "AAPL");

    @Test
    void equalsWithLiteralValue() {
        assertThat(new EqualsRule("8", null).validate(35, FIELDS, null).passed()).isTrue();
        assertThat(new EqualsRule("D", null).validate(35, FIELDS, null).passed()).isFalse();
    }

    @Test
    void equalsPrefersRefOverValue() {
        assertThat(new EqualsRule("ignored", "8").validate(35, FIELDS, null).passed()).isTrue();
    }

    @Test
    void notEqualsPassesWhenDifferentAndWhenAbsent() {
        assertThat(new NotEqualsRule("D", null).validate(35, FIELDS, null).passed()).isTrue();
        assertThat(new NotEqualsRule("8", null).validate(35, FIELDS, null).passed()).isFalse();
        assertThat(new NotEqualsRule("x", null).validate(99, FIELDS, null).passed()).isTrue(); // absent
    }

    @Test
    void enumChecksMembership() {
        assertThat(new EnumRule(List.of("8", "D")).validate(35, FIELDS, null).passed()).isTrue();
        assertThat(new EnumRule(List.of("A", "B")).validate(35, FIELDS, null).passed()).isFalse();
        assertThat(new EnumRule(List.of("8")).validate(99, FIELDS, null).passed()).isFalse(); // absent
    }

    @Test
    void regexMatches() {
        assertThat(new RegexRule("[A-Z]+").validate(55, FIELDS, null).passed()).isTrue();
        assertThat(new RegexRule("\\d+").validate(55, FIELDS, null).passed()).isFalse();
        assertThat(new RegexRule("x").validate(99, FIELDS, null).passed()).isFalse(); // absent
    }

    @Test
    void numericMin() {
        assertThat(new NumericMinRule(5).validate(44, FIELDS, null).passed()).isTrue();
        assertThat(new NumericMinRule(20).validate(44, FIELDS, null).passed()).isFalse();
        assertThat(new NumericMinRule(1).validate(99, FIELDS, null).passed()).isFalse();          // missing
        assertThat(new NumericMinRule(1).validate(55, FIELDS, null).passed()).isFalse();          // not numeric
    }

    @Test
    void numericMax() {
        assertThat(new NumericMaxRule(20).validate(44, FIELDS, null).passed()).isTrue();
        assertThat(new NumericMaxRule(5).validate(44, FIELDS, null).passed()).isFalse();
        assertThat(new NumericMaxRule(20).validate(99, FIELDS, null).passed()).isFalse();         // missing
        assertThat(new NumericMaxRule(20).validate(55, FIELDS, null).passed()).isFalse();         // not numeric
    }

    @Test
    void fieldPresentAndAbsent() {
        assertThat(new FieldPresentRule().validate(35, FIELDS, null).passed()).isTrue();
        assertThat(new FieldPresentRule().validate(99, FIELDS, null).passed()).isFalse();
        assertThat(new FieldAbsentRule().validate(99, FIELDS, null).passed()).isTrue();
        assertThat(new FieldAbsentRule().validate(35, FIELDS, null).passed()).isFalse();
    }

    @Test
    void dateRuleValidatorMustBeDispatchedViaEngine() {
        DateRule dr = new DateRule("d", DateRuleType.CURRENT_TIMESTAMP, null, 0, 0,
                java.util.concurrent.TimeUnit.SECONDS, 0, java.util.concurrent.TimeUnit.SECONDS);
        DateRuleValidator v = new DateRuleValidator(dr);
        assertThat(v.rule()).isSameAs(dr);
        assertThatThrownBy(() -> v.validate(52, FIELDS, null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validationResultFactories() {
        assertThat(ValidationResult.pass(1, "R").passed()).isTrue();
        ValidationResult fail = ValidationResult.fail(1, "R", "exp", "act", "msg");
        assertThat(fail.passed()).isFalse();
        assertThat(fail.message()).isEqualTo("msg");
    }

    // ---- issue #75: substring and length rules ----

    @Test
    void containsFindsASubstringAndFailsWhenAbsent() {
        Map<Integer, String> fields = Fixtures.fields(55, "EUR/USD", 461, "MRCXXX");
        assertThat(new ContainsRule("/", null).validate(55, fields, null).passed()).isTrue();
        assertThat(new ContainsRule("EUR", null).validate(55, fields, null).passed()).isTrue();
        assertThat(new ContainsRule("GBP", null).validate(55, fields, null).passed()).isFalse();
        // a missing field fails: absence is asserted with FIELD_ABSENT, not with a content rule
        assertThat(new ContainsRule("EUR", null).validate(99, fields, null).passed()).isFalse();
    }

    @Test
    void containsPrefersRefOverValue() {
        Map<Integer, String> fields = Fixtures.fields(55, "EUR/USD");
        assertThat(new ContainsRule("ignored", "USD").validate(55, fields, null).passed()).isTrue();
    }

    @Test
    void containsWithNothingToSearchForFails() {
        Map<Integer, String> fields = Fixtures.fields(55, "EUR/USD");
        assertThat(new ContainsRule("", null).validate(55, fields, null).passed()).isFalse();
        assertThat(new ContainsRule(null, null).validate(55, fields, null).passed()).isFalse();
    }

    @Test
    void notContainsIsTheInverseButStillFailsOnAnAbsentField() {
        Map<Integer, String> fields = Fixtures.fields(461, "MRCXXX");
        assertThat(new NotContainsRule("FUT", null).validate(461, fields, null).passed()).isTrue();
        assertThat(new NotContainsRule("XXX", null).validate(461, fields, null).passed()).isFalse();
        assertThat(new NotContainsRule("XXX", null).validate(99, fields, null).passed()).isFalse();
    }

    @Test
    void lengthComparesTheCharacterCount() {
        Map<Integer, String> fields = Fixtures.fields(1, "ACC-001", 11, "ORD-20260824-0001");
        assertThat(new LengthRule(LengthRule.Bound.EXACT, 7).validate(1, fields, null).passed()).isTrue();
        assertThat(new LengthRule(LengthRule.Bound.EXACT, 8).validate(1, fields, null).passed()).isFalse();
        assertThat(new LengthRule(LengthRule.Bound.MIN, 7).validate(1, fields, null).passed()).isTrue();
        assertThat(new LengthRule(LengthRule.Bound.MIN, 8).validate(1, fields, null).passed()).isFalse();
        assertThat(new LengthRule(LengthRule.Bound.MAX, 20).validate(11, fields, null).passed()).isTrue();
        assertThat(new LengthRule(LengthRule.Bound.MAX, 5).validate(11, fields, null).passed()).isFalse();
        assertThat(new LengthRule(LengthRule.Bound.EXACT, 7).validate(99, fields, null).passed()).isFalse();
    }

    @Test
    void lengthReportsUnderTheRuleNameTheScenarioWrote() {
        Map<Integer, String> fields = Fixtures.fields(1, "ACC-001");
        assertThat(new LengthRule(LengthRule.Bound.MIN, 9).validate(1, fields, null).ruleName())
                .isEqualTo("LENGTH_MIN");
        assertThat(new LengthRule(LengthRule.Bound.MAX, 3).validate(1, fields, null).message())
                .isEqualTo("length is 7");
    }
}
