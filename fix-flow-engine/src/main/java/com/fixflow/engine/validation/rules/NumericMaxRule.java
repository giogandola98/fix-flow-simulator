package com.fixflow.engine.validation.rules;

import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

public final class NumericMaxRule implements ValidationRule {
    private final double max;

    public NumericMaxRule(double max) { this.max = max; }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        String actual = fields.get(tag);
        if (actual == null) {
            return ValidationResult.fail(tag, "NUMERIC_MAX", "<=" + max, null, "missing");
        }
        try {
            double v = Double.parseDouble(actual);
            if (v <= max) return ValidationResult.pass(tag, "NUMERIC_MAX");
            return ValidationResult.fail(tag, "NUMERIC_MAX", "<=" + max, actual, "above maximum");
        } catch (NumberFormatException e) {
            return ValidationResult.fail(tag, "NUMERIC_MAX", "<=" + max, actual, "not numeric");
        }
    }
}
