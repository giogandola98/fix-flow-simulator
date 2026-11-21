package com.fixflow.api.rest;

import com.fixflow.api.config.DatabaseAvailability;
import com.fixflow.api.config.GlobalExceptionHandler;
import com.fixflow.engine.operator.OperatorGateway;
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

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = OperatorController.class,
    excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
    }
)
@Import({GlobalExceptionHandler.class, DatabaseAvailability.class})
class OperatorControllerTest {

    @Autowired MockMvc mvc;
    @MockBean OperatorGateway gateway;

    @Test
    void get_returns_the_open_prompt() throws Exception {
        UUID id = UUID.randomUUID();
        when(gateway.open(id)).thenReturn(Optional.of(
                new OperatorGateway.OpenPrompt(id, "op", "Do it", "Done", null)));
        mvc.perform(get("/api/v1/executions/" + id + "/operator"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodeId").value("op"))
            .andExpect(jsonPath("$.instruction").value("Do it"))
            .andExpect(jsonPath("$.executionId").value(id.toString()));
    }

    @Test
    void get_without_a_prompt_is_204() throws Exception {
        UUID id = UUID.randomUUID();
        when(gateway.open(id)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/executions/" + id + "/operator")).andExpect(status().isNoContent());
    }

    @Test
    void answer_is_204_when_a_prompt_was_open() throws Exception {
        UUID id = UUID.randomUUID();
        when(gateway.answer(id, "op", true, "fine")).thenReturn(true);
        mvc.perform(post("/api/v1/executions/" + id + "/operator/op")
                .contentType(MediaType.APPLICATION_JSON).content("{\"ok\":true,\"note\":\"fine\"}"))
            .andExpect(status().isNoContent());
        verify(gateway).answer(id, "op", true, "fine");
    }

    @Test
    void answer_is_404_without_an_open_prompt() throws Exception {
        UUID id = UUID.randomUUID();
        when(gateway.answer(id, "op", false, null)).thenReturn(false);
        mvc.perform(post("/api/v1/executions/" + id + "/operator/op")
                .contentType(MediaType.APPLICATION_JSON).content("{\"ok\":false}"))
            .andExpect(status().isNotFound());
    }
}
