package com.fixflow.api.rest;

import com.fixflow.adapters.quickfixj.QuickFIXAdapter;
import com.fixflow.api.FixFlowApplication;
import com.fixflow.api.support.FakeFixAdapter;
import com.fixflow.core.ports.outbound.DictionaryStorePort;
import com.fixflow.core.ports.outbound.FIXSessionRepositoryPort;
import com.fixflow.engine.execution.SessionExecutionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The pack endpoints over the real context (H2 in memory, fake FIX adapter), importing the
 * engine's generic test pack. The engine version is set to the release that ships packs because
 * the fixture asks for {@code engineMin: "0.8.0"}.
 */
@SpringBootTest(classes = FixFlowApplication.class, properties = {
    "fixflow.version=0.8.0-beta",
    "fixflow.dictionaries.dir=target/pack-controller-test-dictionaries"
})
@AutoConfigureMockMvc
@MockBean(QuickFIXAdapter.class)
class PackControllerTest {

    private static final Path DEMO = Path.of("../fix-flow-engine/src/test/resources/packs/demo");
    private static final String VALUES = "{\"SENDER\":\"SIM\",\"TARGET\":\"SUT\"}";

    @TestConfiguration
    static class FakeAdapterConfig {
        @Bean
        @Primary
        FakeFixAdapter fakeFixAdapter() { return new FakeFixAdapter(); }
    }

    @Autowired MockMvc mvc;
    @Autowired FIXSessionRepositoryPort sessions;
    @Autowired DictionaryStorePort dictionaries;
    @Autowired SessionExecutionRegistry sessionExecutions;

    @AfterEach
    void cleanUp() throws Exception {
        mvc.perform(delete("/api/v1/packs/demo"));
    }

    static byte[] demoZip() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out); Stream<Path> files = Files.walk(DEMO)) {
            for (Path p : files.filter(Files::isRegularFile).sorted().toList()) {
                z.putNextEntry(new ZipEntry(DEMO.relativize(p).toString().replace('\\', '/')));
                z.write(Files.readAllBytes(p));
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static MockMultipartFile file(byte[] zip) {
        return new MockMultipartFile("file", "demo.zip", "application/zip", zip);
    }

    private MvcResult importDemo() throws Exception {
        return mvc.perform(multipart("/api/v1/packs").file(file(demoZip())).param("values", VALUES))
            .andExpect(status().isCreated())
            .andReturn();
    }

    @Test
    void inspect_returns_the_manifest_values_without_installing() throws Exception {
        mvc.perform(multipart("/api/v1/packs/inspect").file(file(demoZip())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("demo"))
            .andExpect(jsonPath("$.engineMin").value("0.8.0"))
            .andExpect(jsonPath("$.values.length()").value(3))
            .andExpect(jsonPath("$.values[0].name").value("SENDER"))
            .andExpect(jsonPath("$.values[0].required").value(true))
            .andExpect(jsonPath("$.values[2].default").value("9876"))
            .andExpect(jsonPath("$.installedVersion").doesNotExist());
        mvc.perform(get("/api/v1/packs")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void import_list_export_delete() throws Exception {
        mvc.perform(multipart("/api/v1/packs").file(file(demoZip())).param("values", VALUES))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.packId").value("demo"))
            .andExpect(jsonPath("$.version").value("1.0.0"))
            .andExpect(jsonPath("$.scenarios").value(2))
            .andExpect(jsonPath("$.datasets").value(1))
            .andExpect(jsonPath("$.sessions").value(1))
            .andExpect(jsonPath("$.dictionaries").value(1));

        mvc.perform(get("/api/v1/packs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("demo"))
            .andExpect(jsonPath("$[0].name").value("Demo pack"))
            .andExpect(jsonPath("$[0].provider").value("Demo"))
            .andExpect(jsonPath("$[0].installedAt").isNotEmpty())
            .andExpect(jsonPath("$[0].counts.scenarios").value(2))
            .andExpect(jsonPath("$[0].counts.dictionaries").value(1));
        mvc.perform(get("/api/v1/scenarios").param("packId", "demo"))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].packId").value("demo"));
        assertThat(dictionaries.list()).contains("demo__FIX50SP2-DEMO.xml");

        // inspecting an installed pack offers the values it was installed with
        mvc.perform(multipart("/api/v1/packs/inspect").file(file(demoZip())))
            .andExpect(jsonPath("$.installedVersion").value("1.0.0"))
            .andExpect(jsonPath("$.installedValues.SENDER").value("SIM"));

        MvcResult export = mvc.perform(get("/api/v1/packs/demo/export"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("demo-1.0.0.zip")))
            .andReturn();
        List<String> entries = new ArrayList<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(export.getResponse().getContentAsByteArray()))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) entries.add(e.getName());
        }
        assertThat(entries).containsExactly("datasets/instruments.csv", "dictionaries/FIX50SP2-DEMO.xml",
            "pack.yaml", "scenarios/demo-main.yaml", "scenarios/demo-send-quote-request.yaml",
            "sessions/demo-acceptor.yaml");

        mvc.perform(delete("/api/v1/packs/demo")).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/packs")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/scenarios").param("packId", "demo")).andExpect(jsonPath("$.length()").value(0));
        assertThat(sessions.findAll()).noneMatch(c -> "demo".equals(c.packId()));
        assertThat(dictionaries.list()).doesNotContain("demo__FIX50SP2-DEMO.xml");
    }

    @Test
    void missing_values_are_a_400_listing_them() throws Exception {
        mvc.perform(multipart("/api/v1/packs").file(file(demoZip())).param("values", "{\"PORT\":\"1\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", allOf(containsString("SENDER"), containsString("TARGET"),
                not(containsString("PORT")))));
    }

    @Test
    void values_that_are_not_a_json_object_are_a_400() throws Exception {
        mvc.perform(multipart("/api/v1/packs").file(file(demoZip())).param("values", "[1,2"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("values")));
    }

    @Test
    void running_execution_is_a_409_naming_it() throws Exception {
        importDemo();
        UUID sessionId = sessions.findAll().stream().filter(c -> "demo".equals(c.packId())).findFirst()
            .orElseThrow().id();
        UUID exec = UUID.randomUUID();
        sessionExecutions.register(sessionId.toString(), exec);
        try {
            mvc.perform(multipart("/api/v1/packs").file(file(demoZip())).param("values", VALUES))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString(exec.toString())));
            mvc.perform(delete("/api/v1/packs/demo"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString(exec.toString())));
        } finally {
            sessionExecutions.unregister(sessionId.toString(), exec);
        }
    }

    @Test
    void unknown_pack_is_a_404() throws Exception {
        mvc.perform(get("/api/v1/packs/nope/export")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/packs/nope")).andExpect(status().isNotFound());
    }

    @Test
    void not_a_zip_is_a_400() throws Exception {
        mvc.perform(multipart("/api/v1/packs").file(file("hello".getBytes())).param("values", VALUES))
            .andExpect(status().isBadRequest());
    }
}
