package com.fixflow.engine.validation.rules;

import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

public final class NumericMinRule implements ValidationRule {
    private final double min;

    public NumericMinRule(double min) { this.min = min; }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        String actual = fields.get(tag);
        if (actual == null) {
            return ValidationResult.fail(tag, "NUMERIC_MIN", ">=" + min, null, "missing");
        }
        try {
            double v = Double.parseDouble(actual);
            if (v >= min) return ValidationResult.pass(tag, "NUMERIC_MIN");
            return ValidationResult.fail(tag, "NUMERIC_MIN", ">=" + min, actual, "below minimum");
        } catch (NumberFormatException e) {
            return ValidationResult.fail(tag, "NUMERIC_MIN", ">=" + min, actual, "not numeric");
        }
    }
}
