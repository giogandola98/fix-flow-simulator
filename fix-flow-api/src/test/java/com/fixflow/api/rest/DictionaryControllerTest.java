package com.fixflow.api.rest;

import com.fixflow.adapters.dictionary.DictionaryStore;
import com.fixflow.adapters.persistence.FIXSessionRepositoryAdapter;
import com.fixflow.api.config.DatabaseAvailability;
import com.fixflow.api.config.GlobalExceptionHandler;
import com.fixflow.core.domain.session.FIXMode;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.domain.session.FIXVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = DictionaryController.class,
    excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
    }
)
@Import({GlobalExceptionHandler.class, DatabaseAvailability.class})
class DictionaryControllerTest {

    @Autowired MockMvc mvc;
    @MockBean DictionaryStore store;
    @MockBean FIXSessionRepositoryAdapter sessionRepo;

    private Path fiveByteFile() throws Exception {
        Path f = Path.of("target", "dictionary-controller-test.xml");
        Files.createDirectories(f.getParent());
        Files.writeString(f, "12345");
        return f;
    }

    @Test
    void listReturnsNamesAndSizes() throws Exception {
        when(store.list()).thenReturn(List.of("A.xml"));
        when(store.resolve("A.xml")).thenReturn(Optional.of(fiveByteFile()));

        mvc.perform(get("/api/v1/dictionaries"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("A.xml"))
            .andExpect(jsonPath("$[0].sizeBytes").value(5));
    }

    @Test
    void uploadReturns201() throws Exception {
        when(store.save(eq("A.xml"), any(InputStream.class))).thenReturn(fiveByteFile());

        mvc.perform(multipart("/api/v1/dictionaries")
                .file(new MockMultipartFile("file", "A.xml", "application/xml", "<fix/>".getBytes())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("A.xml"))
            .andExpect(jsonPath("$.sizeBytes").value(5));
    }

    @Test
    void uploadNameWithDoubleUnderscoreIsReservedForPacks() throws Exception {
        mvc.perform(multipart("/api/v1/dictionaries")
                .file(new MockMultipartFile("file", "acme__FIX44.xml", "application/xml", "<fix/>".getBytes())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("__")));
        org.mockito.Mockito.verifyNoInteractions(store);
    }

    @Test
    void uploadInvalidXmlReturns400() throws Exception {
        when(store.save(eq("A.xml"), any(InputStream.class)))
            .thenThrow(new IllegalArgumentException("not a valid QuickFIX/J dictionary"));

        mvc.perform(multipart("/api/v1/dictionaries")
                .file(new MockMultipartFile("file", "A.xml", "application/xml", "x".getBytes())))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deleteReturns204() throws Exception {
        when(sessionRepo.findAll()).thenReturn(List.of());

        mvc.perform(delete("/api/v1/dictionaries/A.xml")).andExpect(status().isNoContent());
        verify(store).delete("A.xml");
    }

    @Test
    void deleteDictionaryInUseReturns409() throws Exception {
        when(sessionRepo.findAll()).thenReturn(List.of(new FIXSessionConfig(UUID.randomUUID(), "s",
            FIXMode.INITIATOR, FIXVersion.FIX_44, null, "S", "T", "h", 9001, 30, false, false,
            "A.xml", null, false, null)));

        mvc.perform(delete("/api/v1/dictionaries/A.xml")).andExpect(status().isConflict());
    }
}
