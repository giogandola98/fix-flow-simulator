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
}
