package com.fixflow.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.api.rest.dto.ScenarioRequest;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.ports.outbound.ScenarioRepositoryPort;
import com.fixflow.engine.scenario.ScenarioDslParser;
import com.fixflow.engine.scenario.ScenarioRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = ScenarioController.class,
    excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
    }
)
class ScenarioControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean ScenarioRepositoryPort repo;
    @MockBean ScenarioDslParser parser;
    @MockBean ScenarioRegistry registry;

    private Scenario minScenario(UUID id, String name) {
        return new Scenario(id, name, null, "1", null, null, null, null, null, null, null);
    }

    @Test
    void postCreatesScenario() throws Exception {
        UUID id = UUID.randomUUID();
        Scenario s = minScenario(id, "s1");
        when(parser.parseYaml(anyString())).thenReturn(s);
        when(repo.save(any())).thenReturn(s);

        ScenarioRequest req = new ScenarioRequest("s1", "desc", "sess1", "scenario:\n  name: s1");
        mvc.perform(post("/api/v1/scenarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.name").value("s1"));
    }

    @Test
    void getListsScenarios() throws Exception {
        when(repo.findAll()).thenReturn(List.of(minScenario(UUID.randomUUID(), "a")));
        mvc.perform(get("/api/v1/scenarios"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getOneReturnsScenario() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(minScenario(id, "x")));
        mvc.perform(get("/api/v1/scenarios/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete("/api/v1/scenarios/" + id))
            .andExpect(status().isNoContent());
        verify(repo).delete(id);
    }

    @Test
    void exportReturnsYaml() throws Exception {
        UUID id = UUID.randomUUID();
        Scenario s = minScenario(id, "x");
        when(repo.findById(id)).thenReturn(Optional.of(s));
        when(parser.toYaml(s)).thenReturn("scenario: {}");
        mvc.perform(get("/api/v1/scenarios/" + id + "/export"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/x-yaml"))
            .andExpect(content().string("scenario: {}"));
    }
}
