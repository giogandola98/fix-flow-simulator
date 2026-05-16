package com.fixflow.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.api.rest.dto.StartExecutionRequest;
import com.fixflow.core.domain.execution.Execution;
import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.ports.outbound.ExecutionRepositoryPort;
import com.fixflow.engine.execution.ExecutionManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExecutionController.class)
class ExecutionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean ExecutionManager manager;
    @MockBean ExecutionRepositoryPort repo;

    private Execution minExecution(UUID id, ExecutionStatus status) {
        return new Execution(id, UUID.randomUUID(), "1", UUID.randomUUID(),
            status, Instant.now(), null, null, null, null, null);
    }

    @Test
    void startsExecutionReturns202() throws Exception {
        UUID scenarioId = UUID.randomUUID();
        UUID execId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(manager.start(any(), any())).thenReturn(execId);

        mvc.perform(post("/api/v1/scenarios/" + scenarioId + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new StartExecutionRequest(sessionId))))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.executionId").value(execId.toString()));
    }

    @Test
    void stopReturns200() throws Exception {
        UUID execId = UUID.randomUUID();
        mvc.perform(post("/api/v1/executions/" + execId + "/stop"))
            .andExpect(status().isOk());
    }

    @Test
    void getExecutionReturnsDto() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(minExecution(id, ExecutionStatus.RUNNING)));
        mvc.perform(get("/api/v1/executions/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void getEventsReturnsList() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(minExecution(id, ExecutionStatus.RUNNING)));
        mvc.perform(get("/api/v1/executions/" + id + "/events"))
            .andExpect(status().isOk());
    }

    @Test
    void getMessagesReturnsList() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(minExecution(id, ExecutionStatus.PASSED)));
        mvc.perform(get("/api/v1/executions/" + id + "/messages"))
            .andExpect(status().isOk());
    }

    @Test
    void getReportReturnsJson() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(minExecution(id, ExecutionStatus.PASSED)));
        mvc.perform(get("/api/v1/executions/" + id + "/report"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.execution.id").value(id.toString()));
    }
}
