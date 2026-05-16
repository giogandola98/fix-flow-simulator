package com.fixflow.engine.validation;

public record ValidationResult(
    boolean passed,
    int tag,
    String ruleName,
    String expected,
    String actual,
    String message
) {
    public static ValidationResult pass(int tag, String ruleName) {
        return new ValidationResult(true, tag, ruleName, null, null, null);
    }

    public static ValidationResult fail(int tag, String ruleName, String expected, String actual, String message) {
        return new ValidationResult(false, tag, ruleName, expected, actual, message);
    }
}
