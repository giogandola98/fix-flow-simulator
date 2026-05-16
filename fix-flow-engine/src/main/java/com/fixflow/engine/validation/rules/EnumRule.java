package com.fixflow.engine.validation.rules;

import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.List;
import java.util.Map;

public final class EnumRule implements ValidationRule {
    private final List<String> allowed;

    public EnumRule(List<String> allowed) {
        this.allowed = List.copyOf(allowed);
    }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        String actual = fields.get(tag);
        if (actual != null && allowed.contains(actual)) {
            return ValidationResult.pass(tag, "ENUM");
        }
        return ValidationResult.fail(tag, "ENUM", allowed.toString(), actual, "value not in allowed set");
    }
}
