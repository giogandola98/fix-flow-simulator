package com.fixflow.engine.validation;

import java.util.List;

public record ValidationSummary(boolean passed, List<ValidationResult> results) {}
