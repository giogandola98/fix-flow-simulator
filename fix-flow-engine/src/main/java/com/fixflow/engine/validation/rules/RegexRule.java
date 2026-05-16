package com.fixflow.engine.validation.rules;

import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;
import java.util.regex.Pattern;

public final class RegexRule implements ValidationRule {
    private final Pattern pattern;
    private final String raw;

    public RegexRule(String pattern) {
        this.pattern = Pattern.compile(pattern);
        this.raw = pattern;
    }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        String actual = fields.get(tag);
        if (actual != null && pattern.matcher(actual).matches()) {
            return ValidationResult.pass(tag, "REGEX");
        }
        return ValidationResult.fail(tag, "REGEX", raw, actual, "value does not match pattern");
    }
}
