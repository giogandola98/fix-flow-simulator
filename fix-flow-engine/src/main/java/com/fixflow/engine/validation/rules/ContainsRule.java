package com.fixflow.engine.validation.rules;

import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

/**
 * Passes when the field is present and contains {@code expected} as a substring.
 *
 * <p>Like {@link EqualsRule}, a {@code ref} expression takes precedence over the literal value,
 * so a substring can be compared against a value carried by an earlier node.
 *
 * <p>An absent field fails, consistently with the numeric rules: absence is asserted with
 * FIELD_ABSENT, never as a side effect of a content rule.
 */
public final class ContainsRule implements ValidationRule {

    private final String expected;
    private final String refExpression;

    public ContainsRule(String expected, String refExpression) {
        this.expected = expected;
        this.refExpression = refExpression;
    }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        String actual = fields.get(tag);
        String needle = refExpression != null ? refExpression : expected;
        String expectation = "contains \"" + (needle == null ? "" : needle) + "\"";
        if (actual == null) {
            return ValidationResult.fail(tag, "CONTAINS", expectation, null, "missing");
        }
        if (needle == null || needle.isEmpty()) {
            return ValidationResult.fail(tag, "CONTAINS", expectation, actual, "no value to search for");
        }
        return actual.contains(needle)
                ? ValidationResult.pass(tag, "CONTAINS")
                : ValidationResult.fail(tag, "CONTAINS", expectation, actual, "substring not found");
    }
}
