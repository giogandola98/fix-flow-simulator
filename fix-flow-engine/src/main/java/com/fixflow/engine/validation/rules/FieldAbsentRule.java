package com.fixflow.engine.validation.rules;

import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

public final class FieldAbsentRule implements ValidationRule {
    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        if (!fields.containsKey(tag)) return ValidationResult.pass(tag, "FIELD_ABSENT");
        return ValidationResult.fail(tag, "FIELD_ABSENT", "absent", fields.get(tag), "field must not be present");
    }
}
