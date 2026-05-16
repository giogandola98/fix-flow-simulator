package com.fixflow.engine.validation;

import com.fixflow.engine.validation.rules.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationRulesTest {

    @Test
    void equalsRulePassesWhenValueMatches() {
        EqualsRule rule = new EqualsRule("S", null);
        ValidationResult r = rule.validate(35, Map.of(35, "S"), null);
        assertThat(r.passed()).isTrue();
    }

    @Test
    void equalsRuleFailsWhenValueDiffers() {
        EqualsRule rule = new EqualsRule("S", null);
        ValidationResult r = rule.validate(35, Map.of(35, "D"), null);
        assertThat(r.passed()).isFalse();
        assertThat(r.expected()).isEqualTo("S");
        assertThat(r.actual()).isEqualTo("D");
    }

    @Test
    void enumRulePassesWhenInList() {
        EnumRule rule = new EnumRule(List.of("1", "2", "3"));
        assertThat(rule.validate(39, Map.of(39, "2"), null).passed()).isTrue();
    }

    @Test
    void enumRuleFailsWhenNotInList() {
        EnumRule rule = new EnumRule(List.of("1", "2", "3"));
        assertThat(rule.validate(39, Map.of(39, "9"), null).passed()).isFalse();
    }

    @Test
    void regexRulePassesWhenPatternMatches() {
        RegexRule rule = new RegexRule("^ORD-\\d+$");
        assertThat(rule.validate(11, Map.of(11, "ORD-123"), null).passed()).isTrue();
    }

    @Test
    void regexRuleFailsWhenPatternDoesNotMatch() {
        RegexRule rule = new RegexRule("^ORD-\\d+$");
        assertThat(rule.validate(11, Map.of(11, "X"), null).passed()).isFalse();
    }

    @Test
    void numericMinRulePassesWhenAboveMin() {
        NumericMinRule rule = new NumericMinRule(100.0);
        assertThat(rule.validate(38, Map.of(38, "200"), null).passed()).isTrue();
    }

    @Test
    void numericMinRuleFailsWhenBelowMin() {
        NumericMinRule rule = new NumericMinRule(100.0);
        assertThat(rule.validate(38, Map.of(38, "50"), null).passed()).isFalse();
    }

    @Test
    void numericMaxRulePassesWhenBelowMax() {
        NumericMaxRule rule = new NumericMaxRule(100.0);
        assertThat(rule.validate(38, Map.of(38, "50"), null).passed()).isTrue();
    }

    @Test
    void numericMaxRuleFailsWhenAboveMax() {
        NumericMaxRule rule = new NumericMaxRule(100.0);
        assertThat(rule.validate(38, Map.of(38, "150"), null).passed()).isFalse();
    }

    @Test
    void fieldPresentPassesWhenFieldExists() {
        assertThat(new FieldPresentRule().validate(131, Map.of(131, "X"), null).passed()).isTrue();
    }

    @Test
    void fieldPresentFailsWhenFieldMissing() {
        assertThat(new FieldPresentRule().validate(131, Map.of(), null).passed()).isFalse();
    }

    @Test
    void fieldAbsentPassesWhenFieldMissing() {
        assertThat(new FieldAbsentRule().validate(999, Map.of(), null).passed()).isTrue();
    }

    @Test
    void fieldAbsentFailsWhenFieldPresent() {
        assertThat(new FieldAbsentRule().validate(999, Map.of(999, "X"), null).passed()).isFalse();
    }

    @Test
    void notEqualsRulePassesWhenValuesDiffer() {
        assertThat(new NotEqualsRule("D", null).validate(35, Map.of(35, "S"), null).passed()).isTrue();
    }

    @Test
    void notEqualsRuleFailsWhenValuesMatch() {
        assertThat(new NotEqualsRule("D", null).validate(35, Map.of(35, "D"), null).passed()).isFalse();
    }
}
