package com.fixflow.engine.validation.rules;

import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

/**
 * Checks the character length of a field against a bound.
 *
 * <p>One class covers the three DSL rules — {@code LENGTH}, {@code LENGTH_MIN}, {@code LENGTH_MAX}
 * — because they differ only in the comparison; each still reports under its own rule name so the
 * Validation Errors panel names the rule the scenario actually wrote.
 *
 * <p>An absent field fails, like the numeric rules.
 */
public final class LengthRule implements ValidationRule {

    public enum Bound {
        EXACT("LENGTH", "=="),
        MIN("LENGTH_MIN", ">="),
        MAX("LENGTH_MAX", "<=");

        private final String ruleName;
        private final String symbol;

        Bound(String ruleName, String symbol) {
            this.ruleName = ruleName;
            this.symbol = symbol;
        }

        boolean holds(int actual, int expected) {
            return switch (this) {
                case EXACT -> actual == expected;
                case MIN   -> actual >= expected;
                case MAX   -> actual <= expected;
            };
        }
    }

    private final Bound bound;
    private final int length;

    public LengthRule(Bound bound, double length) {
        this.bound = bound;
        this.length = (int) length;
    }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        String actual = fields.get(tag);
        String expectation = "length " + bound.symbol + " " + length;
        if (actual == null) {
            return ValidationResult.fail(tag, bound.ruleName, expectation, null, "missing");
        }
        if (bound.holds(actual.length(), length)) {
            return ValidationResult.pass(tag, bound.ruleName);
        }
        return ValidationResult.fail(tag, bound.ruleName, expectation, actual,
                "length is " + actual.length());
    }
}
