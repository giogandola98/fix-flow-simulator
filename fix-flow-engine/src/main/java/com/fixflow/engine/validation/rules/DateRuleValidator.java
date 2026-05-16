package com.fixflow.engine.validation.rules;

import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.validation.DateRule;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

public final class DateRuleValidator implements ValidationRule {
    private final DateRule rule;

    public DateRuleValidator(DateRule rule) { this.rule = rule; }

    public DateRule rule() { return rule; }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        throw new UnsupportedOperationException("DateRuleValidator must be dispatched via DateRuleEngine");
    }
}
