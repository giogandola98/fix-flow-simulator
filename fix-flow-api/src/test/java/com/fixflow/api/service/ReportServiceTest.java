package com.fixflow.api.service;

import com.fixflow.adapters.persistence.entity.ExecutionEntity;
import com.fixflow.adapters.persistence.entity.ExecutionEventEntity;
import com.fixflow.adapters.persistence.entity.FIXMessageEntity;
import com.fixflow.adapters.persistence.entity.FIXSessionEntity;
import com.fixflow.adapters.persistence.entity.NodeResultEntity;
import com.fixflow.adapters.persistence.entity.ScenarioEntity;
import com.fixflow.adapters.persistence.jpa.JpaExecutionEventRepository;
import com.fixflow.adapters.persistence.jpa.JpaExecutionRepository;
import com.fixflow.adapters.persistence.jpa.JpaFIXMessageRepository;
import com.fixflow.adapters.persistence.jpa.JpaFIXSessionRepository;
import com.fixflow.adapters.persistence.jpa.JpaNodeResultRepository;
import com.fixflow.adapters.persistence.jpa.JpaScenarioRepository;
import com.fixflow.api.dto.ReportDto;
import com.fixflow.core.domain.execution.Direction;
import com.fixflow.core.domain.execution.ExecutionEventType;
import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.session.FIXMode;
import com.fixflow.core.domain.session.FIXVersion;
import com.fixflow.core.ports.outbound.EventPublisherPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for {@link ReportService} against real JPA repositories (H2 in-memory).
 * Persists a full execution graph and verifies the aggregated report.
 */
@SpringBootTest
class ReportServiceTest {

    @MockBean EventPublisherPort eventPublisherPort;

    @Autowired ReportService reportService;
    @Autowired JpaExecutionRepository executionRepo;
    @Autowired JpaExecutionEventRepository eventRepo;
    @Autowired JpaFIXMessageRepository messageRepo;
    @Autowired JpaNodeResultRepository nodeResultRepo;
    @Autowired JpaScenarioRepository scenarioRepo;
    @Autowired JpaFIXSessionRepository sessionRepo;

    @Test
    void buildReportAggregatesNodesMessagesAndErrors() {
        UUID execId = UUID.randomUUID();
        UUID scenarioId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = start.plusMillis(1500);

        ScenarioEntity scn = new ScenarioEntity();
        scn.setId(scenarioId);
        scn.setName("my-scenario");
        scn.setVersion("1");
        scn.setYamlDsl("scenario: {}");
        scenarioRepo.save(scn);

        FIXSessionEntity sess = new FIXSessionEntity();
        sess.setId(sessionId);
        sess.setName("my-session");
        sess.setMode(FIXMode.ACCEPTOR);
        sess.setFixVersion(FIXVersion.FIX_44);
        sess.setSenderCompID("S");
        sess.setTargetCompID("T");
        sess.setHost("localhost");
        sess.setPort(9999);
        sess.setHeartbeatInterval(30);
        sess.setResetOnLogon(true);
        sess.setResetOnLogout(true);
        sessionRepo.save(sess);

        ExecutionEntity exec = new ExecutionEntity();
        exec.setId(execId);
        exec.setScenarioId(scenarioId);
        exec.setScenarioVersion("1");
        exec.setSessionId(sessionId);
        exec.setStatus(ExecutionStatus.FAILED);
        exec.setStartTime(start);
        exec.setEndTime(end);
        executionRepo.save(exec);

        nodeResultRepo.save(node(execId, "n1", "PASSED", start, start.plusMillis(100)));
        nodeResultRepo.save(node(execId, "n2", "FAILED", start.plusMillis(100), start.plusMillis(400)));

        messageRepo.save(message(execId, "8=FIX.4.4|35=D", start));
        messageRepo.save(message(execId, "8=FIX.4.4|35=8", start.plusMillis(200)));

        eventRepo.save(event(execId, ExecutionEventType.ERROR, "validation failed: tag 11", start.plusMillis(300)));
        eventRepo.save(event(execId, ExecutionEventType.NODE_ENTERED, "entered n1", start));

        ReportDto report = reportService.buildReport(execId);

        assertThat(report.executionId()).isEqualTo(execId.toString());
        assertThat(report.scenarioName()).isEqualTo("my-scenario");
        assertThat(report.sessionName()).isEqualTo("my-session");
        assertThat(report.status()).isEqualTo("FAILED");
        assertThat(report.durationMs()).isEqualTo(1500L);
        assertThat(report.nodeResults()).hasSize(2);
        assertThat(report.rawFIXMessages()).hasSize(2);
        assertThat(report.validationErrors()).hasSize(1);
        assertThat(report.validationErrors().get(0).message()).isEqualTo("validation failed: tag 11");
        assertThat(report.statistics()).containsEntry("nodesTotal", 2);
        assertThat(report.statistics()).containsEntry("nodesPassed", 1L);
        assertThat(report.statistics()).containsEntry("nodesFailed", 1L);
        assertThat(report.statistics()).containsEntry("messagesTotal", 2);
    }

    @Test
    void buildReportUsesUnknownWhenScenarioAndSessionMissingAndRunningDuration() {
        UUID execId = UUID.randomUUID();
        ExecutionEntity exec = new ExecutionEntity();
        exec.setId(execId);
        exec.setScenarioId(UUID.randomUUID()); // no matching scenario
        exec.setScenarioVersion("2");
        exec.setSessionId(null);               // null session -> "unknown"
        exec.setStatus(ExecutionStatus.RUNNING);
        exec.setStartTime(Instant.now().minusMillis(50));
        exec.setEndTime(null);                 // running -> now()-start branch
        executionRepo.save(exec);

        ReportDto report = reportService.buildReport(execId);

        assertThat(report.scenarioName()).isEqualTo("unknown");
        assertThat(report.sessionName()).isEqualTo("unknown");
        assertThat(report.status()).isEqualTo("RUNNING");
        assertThat(report.durationMs()).isGreaterThanOrEqualTo(0L);
        assertThat(report.nodeResults()).isEmpty();
    }

    @Test
    void buildReportUnknownExecutionThrows() {
        assertThatThrownBy(() -> reportService.buildReport(UUID.randomUUID()))
            .isInstanceOf(NoSuchElementException.class);
    }

    private NodeResultEntity node(UUID execId, String nodeId, String status, Instant s, Instant e) {
        NodeResultEntity n = new NodeResultEntity();
        n.setId(UUID.randomUUID());
        n.setExecutionId(execId);
        n.setNodeId(nodeId);
        n.setStatus(status);
        n.setStartTime(s);
        n.setEndTime(e);
        return n;
    }

    private FIXMessageEntity message(UUID execId, String raw, Instant at) {
        FIXMessageEntity m = new FIXMessageEntity();
        m.setId(UUID.randomUUID());
        m.setExecutionId(execId);
        m.setDirection(Direction.INBOUND);
        m.setRawFix(raw);
        m.setReceivedAt(at);
        return m;
    }

    private ExecutionEventEntity event(UUID execId, ExecutionEventType type, String detail, Instant at) {
        ExecutionEventEntity ev = new ExecutionEventEntity();
        ev.setId(UUID.randomUUID());
        ev.setExecutionId(execId);
        ev.setType(type);
        ev.setDetail(detail);
        ev.setTimestamp(at);
        return ev;
    }
}
