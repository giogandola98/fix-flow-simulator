package com.fixflow.engine.validation.rules;

import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

/**
 * Passes when the field is present and does NOT contain {@code expected} as a substring.
 *
 * <p>An absent field fails rather than passes. "The tag is not there" is a different assertion
 * from "the tag does not contain X", and letting absence satisfy this rule would turn a typo in a
 * tag number into a green check.
 */
public final class NotContainsRule implements ValidationRule {

    private final String expected;
    private final String refExpression;

    public NotContainsRule(String expected, String refExpression) {
        this.expected = expected;
        this.refExpression = refExpression;
    }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        String actual = fields.get(tag);
        String needle = refExpression != null ? refExpression : expected;
        String expectation = "does not contain \"" + (needle == null ? "" : needle) + "\"";
        if (actual == null) {
            return ValidationResult.fail(tag, "NOT_CONTAINS", expectation, null, "missing");
        }
        if (needle == null || needle.isEmpty()) {
            return ValidationResult.fail(tag, "NOT_CONTAINS", expectation, actual, "no value to search for");
        }
        return actual.contains(needle)
                ? ValidationResult.fail(tag, "NOT_CONTAINS", expectation, actual, "substring found")
                : ValidationResult.pass(tag, "NOT_CONTAINS");
    }
}
