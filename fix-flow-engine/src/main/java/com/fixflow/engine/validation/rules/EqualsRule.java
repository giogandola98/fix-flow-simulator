package com.fixflow.engine.validation.rules;

import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

public final class EqualsRule implements ValidationRule {
    private final String expected;
    private final String refExpression;

    public EqualsRule(String expected, String refExpression) {
        this.expected = expected;
        this.refExpression = refExpression;
    }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        String actual = fields.get(tag);
        String target = refExpression != null ? refExpression : expected;
        if (target != null && target.equals(actual)) {
            return ValidationResult.pass(tag, "EQUALS");
        }
        return ValidationResult.fail(tag, "EQUALS", target, actual, "values differ");
    }
}
