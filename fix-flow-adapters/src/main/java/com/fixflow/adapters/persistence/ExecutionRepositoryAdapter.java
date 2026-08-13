package com.fixflow.adapters.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.adapters.persistence.entity.*;
import com.fixflow.adapters.persistence.jpa.*;
import com.fixflow.core.domain.execution.*;
import com.fixflow.core.ports.outbound.ExecutionRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class ExecutionRepositoryAdapter implements ExecutionRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(ExecutionRepositoryAdapter.class);

    private final JpaExecutionRepository executionRepo;
    private final JpaExecutionEventRepository eventRepo;
    private final JpaFIXMessageRepository messageRepo;
    private final JpaNodeResultRepository nodeResultRepo;
    private final ObjectMapper json = new ObjectMapper();

    public ExecutionRepositoryAdapter(JpaExecutionRepository executionRepo,
                                      JpaExecutionEventRepository eventRepo,
                                      JpaFIXMessageRepository messageRepo,
                                      JpaNodeResultRepository nodeResultRepo) {
        this.executionRepo = executionRepo;
        this.eventRepo = eventRepo;
        this.messageRepo = messageRepo;
        this.nodeResultRepo = nodeResultRepo;
    }

    @Override
    @Transactional
    public Execution save(Execution execution) {
        ExecutionEntity e = executionRepo.findById(execution.id()).orElseGet(ExecutionEntity::new);
        e.setId(execution.id());
        e.setScenarioId(execution.scenarioId());
        e.setScenarioVersion(execution.scenarioVersion());
        e.setSessionId(execution.sessionId());
        e.setStatus(execution.status());
        e.setStartTime(execution.startTime());
        e.setEndTime(execution.endTime());
        e.setCurrentNodeId(execution.currentNodeId());
        e.setVariablesJson(writeJson(execution.variables()));
        executionRepo.save(e);
        return execution;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Execution> findById(UUID id) {
        return executionRepo.findById(id).map(e -> {
            List<ExecutionEvent> events = eventRepo.findByExecutionIdOrderByTimestampAsc(id).stream()
                    .map(ev -> new ExecutionEvent(ev.getId(), ev.getExecutionId(), ev.getType(),
                            ev.getNodeId(), ev.getTimestamp(), ev.getDetail(), ev.getRawFix()))
                    .toList();
            List<NodeResult> nodeResults = nodeResultRepo.findByExecutionIdOrderByStartTimeAsc(id).stream()
                    .map(nr -> new NodeResult(nr.getId(), nr.getExecutionId(), nr.getNodeId(),
                            nr.getStatus(), nr.getStartTime(), nr.getEndTime(), nr.getError()))
                    .toList();
            return new Execution(
                    e.getId(), e.getScenarioId(), e.getScenarioVersion(), e.getSessionId(),
                    e.getStatus(), e.getStartTime(), e.getEndTime(), e.getCurrentNodeId(),
                    readStringMap(e.getVariablesJson()), nodeResults, events);
        });
    }

    @Override
    @Transactional
    public void addEvent(UUID executionId, ExecutionEvent event) {
        ExecutionEventEntity e = new ExecutionEventEntity();
        e.setId(event.id() == null ? UUID.randomUUID() : event.id());
        e.setExecutionId(executionId);
        e.setType(event.type());
        e.setNodeId(event.nodeId());
        e.setTimestamp(event.timestamp());
        e.setDetail(event.detail());
        e.setRawFix(event.rawFix());
        eventRepo.save(e);
    }

    @Override
    @Transactional
    public void addMessage(UUID executionId, FIXMessage message) {
        FIXMessageEntity e = new FIXMessageEntity();
        e.setId(message.id() == null ? UUID.randomUUID() : message.id());
        e.setExecutionId(executionId);
        e.setDirection(message.direction());
        e.setRawFix(message.rawFix());
        e.setFieldsJson(writeJson(message.fields()));
        e.setReceivedAt(message.receivedAt());
        messageRepo.save(e);
    }

    @Override
    @Transactional
    public void addNodeResult(UUID executionId, NodeResult result) {
        NodeResultEntity e = new NodeResultEntity();
        e.setId(result.id() == null ? UUID.randomUUID() : result.id());
        e.setExecutionId(executionId);
        e.setNodeId(result.nodeId());
        e.setStatus(result.status());
        e.setStartTime(result.startTime());
        e.setEndTime(result.endTime());
        e.setError(result.error());
        nodeResultRepo.save(e);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FIXMessage> findMessages(UUID executionId) {
        return messageRepo.findByExecutionIdOrderByReceivedAtAsc(executionId).stream()
                .map(e -> new FIXMessage(e.getId(), e.getExecutionId(), e.getDirection(),
                        e.getRawFix(), readIntMap(e.getFieldsJson()), e.getReceivedAt()))
                .toList();
    }

    private String writeJson(Object obj) {
        try { return json.writeValueAsString(obj == null ? Map.of() : obj); }
        catch (JsonProcessingException ex) { throw new UncheckedIOException(ex); }
    }

    private Map<String, String> readStringMap(String s) {
        if (s == null || s.isBlank()) return Map.of();
        try { return json.readValue(s, new TypeReference<Map<String, String>>() {}); }
        catch (Exception ex) {
            log.warn("Failed to parse string map from JSON [{}], returning empty map", s, ex);
            return Map.of();
        }
    }

    private Map<Integer, String> readIntMap(String s) {
        if (s == null || s.isBlank()) return Map.of();
        try {
            Map<String, String> raw = json.readValue(s, new TypeReference<Map<String, String>>() {});
            Map<Integer, String> result = new java.util.HashMap<>();
            raw.forEach((k, v) -> result.put(Integer.parseInt(k), v));
            return result;
        } catch (Exception ex) {
            log.warn("Failed to parse int map from JSON [{}], returning empty map", s, ex);
            return Map.of();
        }
    }
}
