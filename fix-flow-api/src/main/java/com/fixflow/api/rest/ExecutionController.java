package com.fixflow.api.rest;

import com.fixflow.api.rest.dto.ExecutionDto;
import com.fixflow.api.rest.dto.ExecutionReportDto;
import com.fixflow.api.rest.dto.StartExecutionRequest;
import com.fixflow.core.domain.execution.Execution;
import com.fixflow.core.domain.execution.ExecutionEvent;
import com.fixflow.core.domain.execution.ExecutionEventType;
import com.fixflow.core.ports.outbound.ExecutionRepositoryPort;
import com.fixflow.engine.execution.ExecutionManager;
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

    public ExecutionController(ExecutionManager manager, ExecutionRepositoryPort repo) {
        this.manager = manager;
        this.repo = repo;
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
    public List<ExecutionEvent> messages(@PathVariable UUID id) {
        return load(id).events().stream()
            .filter(e -> e.type() == ExecutionEventType.MESSAGE_SENT
                      || e.type() == ExecutionEventType.MESSAGE_RECEIVED)
            .toList();
    }

    @GetMapping("/api/v1/executions/{id}/report")
    public ExecutionReportDto report(@PathVariable UUID id) {
        return ExecutionReportDto.from(load(id));
    }

    private Execution load(UUID id) {
        return repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("execution not found: " + id));
    }
}
