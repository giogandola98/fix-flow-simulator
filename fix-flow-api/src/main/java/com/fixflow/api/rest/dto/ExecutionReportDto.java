package com.fixflow.api.rest.dto;

import com.fixflow.core.domain.execution.Execution;
import com.fixflow.core.domain.execution.ExecutionEvent;
import com.fixflow.core.domain.execution.NodeResult;

import java.util.List;

public record ExecutionReportDto(
    ExecutionDto execution,
    List<ExecutionEvent> events,
    List<NodeResult> nodeResults
) {
    public static ExecutionReportDto from(Execution e) {
        return new ExecutionReportDto(
            ExecutionDto.from(e),
            e.events(),
            e.nodeResults()
        );
    }
}
