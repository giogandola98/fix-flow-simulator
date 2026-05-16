package com.fixflow.engine.validation;

import java.util.List;
import java.util.Map;

public record ValidationConfig(
    List<ValidationRuleConfig> validations,
    Map<String, DateRule> dateRules,
    boolean strictMode
) {}
