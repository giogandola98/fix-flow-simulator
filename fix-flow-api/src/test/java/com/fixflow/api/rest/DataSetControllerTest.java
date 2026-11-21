package com.fixflow.api.rest;

import com.fixflow.api.config.DatabaseAvailability;
import com.fixflow.api.config.GlobalExceptionHandler;
import com.fixflow.core.domain.dataset.DataSet;
import com.fixflow.core.ports.outbound.DataSetRepositoryPort;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = DataSetController.class,
    excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
    }
)
@Import({GlobalExceptionHandler.class, DatabaseAvailability.class})
class DataSetControllerTest {

    @Autowired MockMvc mvc;
    @MockBean DataSetRepositoryPort repo;

    private static DataSet sample(UUID id, String name) {
        return new DataSet(id, name, List.of("a", "b"), List.of(List.of("1", "2")), null);
    }

    private void echoSave() {
        when(repo.save(any())).thenAnswer(i -> {
            DataSet d = i.getArgument(0);
            return new DataSet(d.id() != null ? d.id() : UUID.randomUUID(), d.name(), d.columns(), d.rows(), d.packId());
        });
    }

    @Test
    void listReturnsSummaries() throws Exception {
        when(repo.findAll()).thenReturn(List.of(sample(UUID.randomUUID(), "d1")));
        mvc.perform(get("/api/v1/datasets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("d1"))
            .andExpect(jsonPath("$[0].rowCount").value(1))
            .andExpect(jsonPath("$[0].columns[1]").value("b"))
            .andExpect(jsonPath("$[0].rows").doesNotExist());
    }

    @Test
    void getReturnsFullAnd404() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(sample(id, "d1")));
        mvc.perform(get("/api/v1/datasets/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[0][1]").value("2"));
        mvc.perform(get("/api/v1/datasets/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void createReturns201() throws Exception {
        echoSave();
        when(repo.findByName("d1")).thenReturn(Optional.empty());
        mvc.perform(post("/api/v1/datasets").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"d1\",\"columns\":[\"a\",\"b\"],\"rows\":[[\"1\",\"2\"]]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.rowCount").value(1));
    }

    @Test
    void createDuplicateNameReturns409() throws Exception {
        when(repo.findByName("d1")).thenReturn(Optional.of(sample(UUID.randomUUID(), "d1")));
        mvc.perform(post("/api/v1/datasets").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"d1\",\"columns\":[\"a\"],\"rows\":[]}"))
            .andExpect(status().isConflict());
        verify(repo, never()).save(any());
    }

    @Test
    void createRaggedRowReturns400() throws Exception {
        mvc.perform(post("/api/v1/datasets").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"d1\",\"columns\":[\"a\",\"b\"],\"rows\":[[\"1\"]]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateKeepsIdAndAllowsOwnName() throws Exception {
        UUID id = UUID.randomUUID();
        echoSave();
        when(repo.findById(id)).thenReturn(Optional.of(sample(id, "d1")));
        when(repo.findByName("d1")).thenReturn(Optional.of(sample(id, "d1")));
        mvc.perform(put("/api/v1/datasets/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"d1\",\"columns\":[\"a\"],\"rows\":[[\"9\"]]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void updateToNameOfAnotherReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(sample(id, "d1")));
        when(repo.findByName("d2")).thenReturn(Optional.of(sample(UUID.randomUUID(), "d2")));
        mvc.perform(put("/api/v1/datasets/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"d2\",\"columns\":[\"a\"],\"rows\":[]}"))
            .andExpect(status().isConflict());
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(sample(id, "d1")));
        mvc.perform(delete("/api/v1/datasets/" + id)).andExpect(status().isNoContent());
        verify(repo).delete(id);
    }

    @Test
    void importCsvReturns201() throws Exception {
        echoSave();
        when(repo.findByName("rates")).thenReturn(Optional.empty());
        byte[] csv = "﻿a,b\r\n1,\"x,y\"\r\n".getBytes(StandardCharsets.UTF_8);
        mvc.perform(multipart("/api/v1/datasets/import")
                .file(new MockMultipartFile("file", "r.csv", "text/csv", csv))
                .param("name", "rates"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("rates"))
            .andExpect(jsonPath("$.rows[0][1]").value("x,y"));
        ArgumentCaptor<DataSet> c = ArgumentCaptor.forClass(DataSet.class);
        verify(repo).save(c.capture());
        assertThat(c.getValue().columns()).containsExactly("a", "b");
    }

    @Test
    void importRaggedCsvReturns400NamingLine() throws Exception {
        when(repo.findByName("rates")).thenReturn(Optional.empty());
        mvc.perform(multipart("/api/v1/datasets/import")
                .file(new MockMultipartFile("file", "r.csv", "text/csv", "a,b,c\n1,2,3\n4,5\n".getBytes()))
                .param("name", "rates"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("line 3: 2 values, header has 3"));
        verify(repo, never()).save(any());
    }

    @Test
    void importDuplicateNameReturns409() throws Exception {
        when(repo.findByName("rates")).thenReturn(Optional.of(sample(UUID.randomUUID(), "rates")));
        mvc.perform(multipart("/api/v1/datasets/import")
                .file(new MockMultipartFile("file", "r.csv", "text/csv", "a\n1\n".getBytes()))
                .param("name", "rates"))
            .andExpect(status().isConflict());
    }

    @Test
    void exportReturnsCsv() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(
            new DataSet(id, "d1", List.of("a", "b"), List.of(List.of("1", "x,y")), null)));
        mvc.perform(get("/api/v1/datasets/" + id + "/export"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/csv"))
            .andExpect(header().string("Content-Disposition", containsString("d1.csv")))
            .andExpect(content().string("a,b\n1,\"x,y\"\n"));
    }
}
