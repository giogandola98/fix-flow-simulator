package com.fixflow.engine.validation;

import com.fixflow.engine.execution.ExecutionContext;

import java.util.Map;

public interface ValidationRule {
    ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx);
}
