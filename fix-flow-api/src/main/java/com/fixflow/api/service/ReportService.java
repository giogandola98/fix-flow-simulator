package com.fixflow.api.service;

import com.fixflow.adapters.persistence.entity.ExecutionEventEntity;
import com.fixflow.adapters.persistence.entity.FIXMessageEntity;
import com.fixflow.adapters.persistence.entity.NodeResultEntity;
import com.fixflow.adapters.persistence.jpa.JpaExecutionEventRepository;
import com.fixflow.adapters.persistence.jpa.JpaExecutionRepository;
import com.fixflow.adapters.persistence.jpa.JpaFIXMessageRepository;
import com.fixflow.adapters.persistence.jpa.JpaFIXSessionRepository;
import com.fixflow.adapters.persistence.jpa.JpaNodeResultRepository;
import com.fixflow.adapters.persistence.jpa.JpaScenarioRepository;
import com.fixflow.api.dto.ReportDto;
import com.fixflow.core.domain.execution.ExecutionEventType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ReportService {

    private final JpaExecutionRepository executionRepo;
    private final JpaExecutionEventRepository eventRepo;
    private final JpaFIXMessageRepository messageRepo;
    private final JpaNodeResultRepository nodeResultRepo;
    private final JpaScenarioRepository scenarioRepo;
    private final JpaFIXSessionRepository sessionRepo;

    public ReportService(
            JpaExecutionRepository executionRepo,
            JpaExecutionEventRepository eventRepo,
            JpaFIXMessageRepository messageRepo,
            JpaNodeResultRepository nodeResultRepo,
            JpaScenarioRepository scenarioRepo,
            JpaFIXSessionRepository sessionRepo) {
        this.executionRepo = executionRepo;
        this.eventRepo = eventRepo;
        this.messageRepo = messageRepo;
        this.nodeResultRepo = nodeResultRepo;
        this.scenarioRepo = scenarioRepo;
        this.sessionRepo = sessionRepo;
    }

    @Transactional(readOnly = true)
    public ReportDto buildReport(UUID executionId) {
        var execution = executionRepo.findById(executionId)
                .orElseThrow(() -> new NoSuchElementException("execution not found: " + executionId));

        String scenarioName = scenarioRepo.findById(execution.getScenarioId())
                .map(s -> s.getName())
                .orElse("unknown");

        String sessionName = execution.getSessionId() != null
                ? sessionRepo.findById(execution.getSessionId())
                        .map(s -> s.getName())
                        .orElse("unknown")
                : "unknown";

        Instant startTime = execution.getStartTime();
        Instant endTime = execution.getEndTime();
        long durationMs = 0;
        if (startTime != null && endTime != null) {
            durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();
        } else if (startTime != null) {
            durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();
        }

        // Node results
        List<NodeResultEntity> nodeResultEntities =
                nodeResultRepo.findByExecutionIdOrderByStartTimeAsc(executionId);

        List<ReportDto.NodeResultDto> nodeResults = nodeResultEntities.stream().map(nr -> {
            long nodeDuration = 0;
            if (nr.getStartTime() != null && nr.getEndTime() != null) {
                nodeDuration = nr.getEndTime().toEpochMilli() - nr.getStartTime().toEpochMilli();
            }
            return new ReportDto.NodeResultDto(nr.getNodeId(), nr.getNodeId(), nr.getStatus(), nodeDuration);
        }).toList();

        // Raw FIX messages
        List<FIXMessageEntity> messages = messageRepo.findByExecutionIdOrderByReceivedAtAsc(executionId);
        List<String> rawFIXMessages = messages.stream()
                .map(FIXMessageEntity::getRawFix)
                .filter(r -> r != null && !r.isBlank())
                .toList();

        // Validation errors from events
        List<ExecutionEventEntity> events =
                eventRepo.findByExecutionIdOrderByTimestampAsc(executionId);

        List<ReportDto.ValidationErrorDto> validationErrors = new ArrayList<>();
        for (ExecutionEventEntity event : events) {
            if (event.getType() == ExecutionEventType.ERROR && event.getDetail() != null) {
                validationErrors.add(new ReportDto.ValidationErrorDto(
                        0, "ERROR", "", "", event.getDetail()));
            }
        }

        // Statistics
        long nodesPassed = nodeResultEntities.stream()
                .filter(nr -> "PASSED".equalsIgnoreCase(nr.getStatus()))
                .count();
        long nodesFailed = nodeResultEntities.stream()
                .filter(nr -> "FAILED".equalsIgnoreCase(nr.getStatus()))
                .count();

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("nodesTotal", nodeResultEntities.size());
        statistics.put("nodesPassed", nodesPassed);
        statistics.put("nodesFailed", nodesFailed);
        statistics.put("messagesTotal", messages.size());
        statistics.put("eventsTotal", events.size());

        return new ReportDto(
                executionId.toString(),
                scenarioName,
                execution.getScenarioVersion(),
                sessionName,
                execution.getStatus() != null ? execution.getStatus().name() : "UNKNOWN",
                startTime != null ? startTime.toString() : null,
                endTime != null ? endTime.toString() : null,
                durationMs,
                nodeResults,
                rawFIXMessages,
                validationErrors,
                statistics
        );
    }
}
