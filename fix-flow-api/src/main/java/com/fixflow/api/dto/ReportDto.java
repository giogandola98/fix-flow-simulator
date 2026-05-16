package com.fixflow.api.dto;

import java.util.List;
import java.util.Map;

public record ReportDto(
    String executionId,
    String scenarioName,
    String scenarioVersion,
    String sessionName,
    String status,
    String startTime,
    String endTime,
    long durationMs,
    List<NodeResultDto> nodeResults,
    List<String> rawFIXMessages,
    List<ValidationErrorDto> validationErrors,
    Map<String, Object> statistics
) {
    public record NodeResultDto(String nodeId, String nodeName, String status, long durationMs) {}
    public record ValidationErrorDto(int tag, String rule, String expected, String actual, String message) {}
}
