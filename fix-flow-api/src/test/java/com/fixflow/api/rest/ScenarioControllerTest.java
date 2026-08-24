package com.fixflow.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.api.config.GlobalExceptionHandler;
import com.fixflow.api.rest.dto.ScenarioRequest;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.ports.outbound.ScenarioRepositoryPort;
import com.fixflow.engine.scenario.ScenarioDslParser;
import com.fixflow.engine.scenario.ScenarioRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
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
@Import(GlobalExceptionHandler.class)
class ScenarioControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean ScenarioRepositoryPort repo;
    @MockBean ScenarioDslParser parser;
    @MockBean ScenarioRegistry registry;

    private Scenario scenario(UUID id, String name) {
        return new Scenario(id, name, "desc", "1", "sess", null, null, null, null, null, null, null);
    }

    @Test
    void createFromYamlReturns201AndRegisters() throws Exception {
        UUID id = UUID.randomUUID();
        Scenario s = scenario(id, "s1");
        when(parser.parseYaml(anyString())).thenReturn(s);
        when(repo.save(any())).thenReturn(s);

        ScenarioRequest req = new ScenarioRequest("s1", "desc", "sess", "scenario:\n  name: s1");
        mvc.perform(post("/api/v1/scenarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.name").value("s1"));
        verify(registry).register(s);
    }

    @Test
    void createWithoutYamlBuildsScenarioFromFields() throws Exception {
        // No yamlDsl -> controller synthesizes a Scenario from name/description/sessionRef.
        ArgumentCaptor<Scenario> captor = ArgumentCaptor.forClass(Scenario.class);
        when(repo.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        ScenarioRequest req = new ScenarioRequest("manual", "d", "sref", null);
        mvc.perform(post("/api/v1/scenarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("manual"));

        assertThat(captor.getValue().name()).isEqualTo("manual");
        assertThat(captor.getValue().sessionRef()).isEqualTo("sref");
    }

    @Test
    void listReturnsScenarios() throws Exception {
        when(repo.findAll()).thenReturn(List.of(
            scenario(UUID.randomUUID(), "a"), scenario(UUID.randomUUID(), "b")));
        mvc.perform(get("/api/v1/scenarios"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getOneReturnsScenarioWithYaml() throws Exception {
        UUID id = UUID.randomUUID();
        Scenario s = scenario(id, "x");
        when(repo.findById(id)).thenReturn(Optional.of(s));
        when(parser.toYaml(s)).thenReturn("scenario: {name: x}");
        mvc.perform(get("/api/v1/scenarios/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.yamlDsl").value("scenario: {name: x}"));
    }

    @Test
    void getUnknownReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/scenarios/" + id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateWithoutYamlKeepsExistingAndOverridesName() throws Exception {
        UUID id = UUID.randomUUID();
        Scenario existing = scenario(id, "old");
        when(repo.findById(id)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(parser.toYaml(any())).thenReturn("y");

        ScenarioRequest req = new ScenarioRequest("newname", null, null, null);
        mvc.perform(put("/api/v1/scenarios/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("newname"));
        verify(registry).register(any());
    }

    @Test
    void updateUnknownReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        mvc.perform(put("/api/v1/scenarios/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new ScenarioRequest("n", null, null, null))))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete("/api/v1/scenarios/" + id))
            .andExpect(status().isNoContent());
        verify(repo).delete(id);
    }

    @Test
    void createWithYamlReturnsYamlInResponseBody() throws Exception {
        // Regression: create() must return the same yamlDsl the client submitted,
        // otherwise the UI canvas renders empty right after import/create.
        UUID id = UUID.randomUUID();
        String yamlText = "scenario:\n  name: s1\n  nodes:\n    - id: n1\n";
        Scenario s = new Scenario(id, "s1", "desc", "1", "sess", null, null, null, null, null, null, yamlText);
        when(parser.parseYaml(anyString())).thenReturn(s);
        when(repo.save(any())).thenReturn(s);

        ScenarioRequest req = new ScenarioRequest("s1", "desc", "sess", yamlText);
        mvc.perform(post("/api/v1/scenarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.yamlDsl").value(yamlText));
    }

    @Test
    void createWithoutYamlStillReturnsWellFormedResponse() throws Exception {
        // No yamlDsl supplied: controller must still respond cleanly (no 500),
        // falling back to parser.toYaml(...) the same way get()/update() do.
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(parser.toYaml(any())).thenReturn("scenario: {name: manual}");

        ScenarioRequest req = new ScenarioRequest("manual", "d", "sref", null);
        mvc.perform(post("/api/v1/scenarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("manual"))
            .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void importUtf8FileReturns201AndDecodesUtf8() throws Exception {
        UUID id = UUID.randomUUID();
        Scenario s = scenario(id, "imported");
        // Non-ASCII content to prove UTF-8 decoding.
        String yamlText = "scenario:\n  name: imported-éü-中文";
        when(parser.parseYaml(anyString())).thenReturn(s);
        when(repo.save(any())).thenReturn(s);

        MockMultipartFile file = new MockMultipartFile(
            "file", "scenario.yaml", "application/x-yaml",
            yamlText.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/v1/scenarios/import").file(file))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id.toString()));

        ArgumentCaptor<String> yamlCaptor = ArgumentCaptor.forClass(String.class);
        verify(parser).parseYaml(yamlCaptor.capture());
        assertThat(yamlCaptor.getValue()).isEqualTo(yamlText);
        verify(registry).register(s);
    }

    @Test
    void importYamlReturns201AndBodyContainsYaml() throws Exception {
        // Regression: importYaml() must return the imported yamlDsl, otherwise the
        // canvas shows zero nodes right after a template import (server bug, not UI).
        UUID id = UUID.randomUUID();
        String yamlText = "scenario:\n  name: imported-tpl\n  nodes:\n    - id: start-node\n";
        Scenario s = new Scenario(id, "imported-tpl", "desc", "1", "sess", null, null, null, null, null, null, yamlText);
        when(parser.parseYaml(anyString())).thenReturn(s);
        when(repo.save(any())).thenReturn(s);

        MockMultipartFile file = new MockMultipartFile(
            "file", "scenario.yaml", "application/x-yaml",
            yamlText.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/v1/scenarios/import").file(file))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.yamlDsl").value(containsString("start-node")))
            .andExpect(jsonPath("$.yamlDsl").value(containsString("imported-tpl")));
    }

    @Test
    void importEmptyFileReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "empty.yaml", "application/x-yaml", new byte[0]);

        mvc.perform(multipart("/api/v1/scenarios/import").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void exportSetsSanitizedContentDisposition() throws Exception {
        UUID id = UUID.randomUUID();
        // Name containing path/quote/newline characters that must be scrubbed.
        Scenario s = scenario(id, "evil/na\"me\nbreak\\x");
        when(repo.findById(id)).thenReturn(Optional.of(s));
        when(parser.toYaml(s)).thenReturn("scenario: {}");

        mvc.perform(get("/api/v1/scenarios/" + id + "/export"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/x-yaml"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"evil_na_me_break_x.yaml\""))
            .andExpect(content().string("scenario: {}"));
    }

    @Test
    void exportUnknownReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/scenarios/" + id + "/export"))
            .andExpect(status().isNotFound());
    }
}
