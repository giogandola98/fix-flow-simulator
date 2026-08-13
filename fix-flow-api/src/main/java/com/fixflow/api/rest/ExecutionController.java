package com.fixflow.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.api.dto.ReportDto;
import com.fixflow.api.rest.dto.ExecutionDto;
import com.fixflow.api.rest.dto.StartExecutionRequest;
import com.fixflow.api.service.ReportService;
import com.fixflow.core.domain.execution.Execution;
import com.fixflow.core.domain.execution.ExecutionEvent;
import com.fixflow.core.domain.execution.FIXMessage;
import com.fixflow.core.ports.outbound.ExecutionRepositoryPort;
import com.fixflow.engine.execution.ExecutionManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
public class ExecutionController {

    private final ExecutionManager manager;
    private final ExecutionRepositoryPort repo;
    private final ReportService reportService;
    private final ObjectMapper objectMapper;

    public ExecutionController(ExecutionManager manager, ExecutionRepositoryPort repo,
                               ReportService reportService, ObjectMapper objectMapper) {
        this.manager = manager;
        this.repo = repo;
        this.reportService = reportService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/v1/scenarios/{scenarioId}/execute")
    public ResponseEntity<Map<String, UUID>> start(
        @PathVariable UUID scenarioId,
        @RequestBody StartExecutionRequest req
    ) {
        UUID execId = manager.start(scenarioId, req.sessionId());
        return ResponseEntity
            .accepted()
            .location(URI.create("/api/v1/executions/" + execId))
            .body(Map.of("executionId", execId));
    }

    @PostMapping("/api/v1/executions/{id}/stop")
    public ResponseEntity<Void> stop(@PathVariable UUID id) {
        manager.stop(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/v1/executions/{id}")
    public ExecutionDto get(@PathVariable UUID id) {
        return ExecutionDto.from(load(id));
    }

    @GetMapping("/api/v1/executions/{id}/events")
    public List<ExecutionEvent> events(@PathVariable UUID id) {
        return load(id).events();
    }

    @GetMapping("/api/v1/executions/{id}/messages")
    public List<FIXMessage> messages(@PathVariable UUID id) {
        return repo.findMessages(id);
    }

    @GetMapping("/api/v1/executions/{id}/report")
    public ReportDto getReport(@PathVariable UUID id) {
        return reportService.buildReport(id);
    }

    @GetMapping("/api/v1/executions/{id}/report/download")
    public ResponseEntity<byte[]> downloadReport(@PathVariable UUID id) throws Exception {
        ReportDto report = reportService.buildReport(id);
        byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"execution-" + id + "-report.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(bytes);
    }

    private Execution load(UUID id) {
        return repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("execution not found: " + id));
    }
}
