package com.fixflow.engine.validation.rules;

import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

public final class NotEqualsRule implements ValidationRule {
    private final String unexpected;
    private final String refExpression;

    public NotEqualsRule(String unexpected, String refExpression) {
        this.unexpected = unexpected;
        this.refExpression = refExpression;
    }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        String actual = fields.get(tag);
        String target = refExpression != null ? refExpression : unexpected;
        if (target == null || !target.equals(actual)) {
            return ValidationResult.pass(tag, "NOT_EQUALS");
        }
        return ValidationResult.fail(tag, "NOT_EQUALS", "!= " + target, actual, "values must differ");
    }
}
