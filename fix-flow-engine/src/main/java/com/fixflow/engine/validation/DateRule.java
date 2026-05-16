package com.fixflow.engine.validation;

import java.util.concurrent.TimeUnit;

public record DateRule(
    String id,
    DateRuleType type,
    String sourceNode,
    int sourceTag,
    long offsetValue,
    TimeUnit offsetUnit,
    long toleranceValue,
    TimeUnit toleranceUnit
) {}
