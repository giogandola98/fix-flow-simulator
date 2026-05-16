package com.fixflow.engine.validation;

import java.util.List;

public record ValidationRuleConfig(
    int tag,
    String rule,
    String value,
    List<String> values,
    String ref,
    String dateRule,
    String pattern,
    double numericValue
) {}
