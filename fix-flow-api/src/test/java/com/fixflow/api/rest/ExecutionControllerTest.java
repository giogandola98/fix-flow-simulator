package com.fixflow.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.api.config.GlobalExceptionHandler;
import com.fixflow.api.dto.ReportDto;
import com.fixflow.api.rest.dto.StartExecutionRequest;
import com.fixflow.api.service.ReportService;
import com.fixflow.core.domain.execution.Direction;
import com.fixflow.core.domain.execution.Execution;
import com.fixflow.core.domain.execution.ExecutionEvent;
import com.fixflow.core.domain.execution.ExecutionEventType;
import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.execution.FIXMessage;
import com.fixflow.core.ports.outbound.ExecutionRepositoryPort;
import com.fixflow.engine.execution.ExecutionManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = ExecutionController.class,
    excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
    }
)
@Import(GlobalExceptionHandler.class)
class ExecutionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean ExecutionManager manager;
    @MockBean ExecutionRepositoryPort repo;
    @MockBean ReportService reportService;

    private Execution execution(UUID id, ExecutionStatus status) {
        return new Execution(id, UUID.randomUUID(), "1", UUID.randomUUID(),
            status, Instant.now(), null, "node-1", null, null, null);
    }

    @Test
    void executeReturns202WithExecutionId() throws Exception {
        UUID scenarioId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID execId = UUID.randomUUID();
        when(manager.start(eq(scenarioId), eq(sessionId))).thenReturn(execId);

        mvc.perform(post("/api/v1/scenarios/" + scenarioId + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new StartExecutionRequest(sessionId))))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", "/api/v1/executions/" + execId))
            .andExpect(jsonPath("$.executionId").value(execId.toString()));

        verify(manager).start(scenarioId, sessionId);
    }

    @Test
    void stopReturns200() throws Exception {
        UUID execId = UUID.randomUUID();
        mvc.perform(post("/api/v1/executions/" + execId + "/stop"))
            .andExpect(status().isOk());
        verify(manager).stop(execId);
    }

    @Test
    void getReturnsExecutionDto() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(execution(id, ExecutionStatus.RUNNING)));

        mvc.perform(get("/api/v1/executions/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.status").value("RUNNING"))
            .andExpect(jsonPath("$.currentNodeId").value("node-1"));
    }

    @Test
    void getUnknownExecutionReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/executions/" + id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getEventsReturnsList() throws Exception {
        UUID id = UUID.randomUUID();
        ExecutionEvent ev = new ExecutionEvent(UUID.randomUUID(), id,
            ExecutionEventType.NODE_ENTERED, "node-1", Instant.now(), "entered", null);
        when(repo.findById(id)).thenReturn(Optional.of(
            new Execution(id, UUID.randomUUID(), "1", UUID.randomUUID(),
                ExecutionStatus.RUNNING, Instant.now(), null, "node-1", null, null, List.of(ev))));

        mvc.perform(get("/api/v1/executions/" + id + "/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].type").value("NODE_ENTERED"));
    }

    @Test
    void getEventsUnknownExecutionReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/executions/" + id + "/events"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getMessagesQueriesRepoDirectly() throws Exception {
        UUID id = UUID.randomUUID();
        FIXMessage msg = new FIXMessage(UUID.randomUUID(), id, Direction.INBOUND,
            "8=FIX.4.4", Map.of(35, "D"), Instant.now());
        when(repo.findMessages(id)).thenReturn(List.of(msg));

        mvc.perform(get("/api/v1/executions/" + id + "/messages"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].direction").value("INBOUND"));
        verify(repo).findMessages(id);
    }

    @Test
    void getReportReturnsJson() throws Exception {
        UUID id = UUID.randomUUID();
        ReportDto report = new ReportDto(id.toString(), "scn", "1", "sess", "PASSED",
            null, null, 0L, List.of(), List.of(), List.of(), Map.of());
        when(reportService.buildReport(id)).thenReturn(report);

        mvc.perform(get("/api/v1/executions/" + id + "/report"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.executionId").value(id.toString()))
            .andExpect(jsonPath("$.status").value("PASSED"));
    }

    @Test
    void downloadReportSetsContentDisposition() throws Exception {
        UUID id = UUID.randomUUID();
        ReportDto report = new ReportDto(id.toString(), "scn", "1", "sess", "PASSED",
            null, null, 0L, List.of(), List.of(), List.of(), Map.of());
        when(reportService.buildReport(id)).thenReturn(report);

        mvc.perform(get("/api/v1/executions/" + id + "/report/download"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",
                "attachment; filename=\"execution-" + id + "-report.json\""))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void reportUnknownExecutionReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(reportService.buildReport(id))
            .thenThrow(new NoSuchElementException("execution not found: " + id));
        mvc.perform(get("/api/v1/executions/" + id + "/report"))
            .andExpect(status().isNotFound());
    }

    @Test
    void malformedUuidPathVariableReturns400() throws Exception {
        mvc.perform(get("/api/v1/executions/not-a-uuid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void wrongHttpMethodReturns405() throws Exception {
        UUID id = UUID.randomUUID();
        // /stop is POST-only
        mvc.perform(get("/api/v1/executions/" + id + "/stop"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.status").value(405));
    }
}
