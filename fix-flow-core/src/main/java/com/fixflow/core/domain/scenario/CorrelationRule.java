package com.fixflow.core.domain.scenario;

public record CorrelationRule(int sourceTag, String targetNode, int targetTag, long timeWindowMs) {}
