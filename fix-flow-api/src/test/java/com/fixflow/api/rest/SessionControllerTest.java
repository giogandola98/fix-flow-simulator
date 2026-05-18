package com.fixflow.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.adapters.persistence.FIXSessionRepositoryAdapter;
import com.fixflow.api.rest.dto.FIXSessionRequest;
import com.fixflow.core.domain.session.FIXMode;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.domain.session.FIXVersion;
import com.fixflow.engine.fix.FIXSessionManager;
import com.fixflow.engine.fix.HotReloadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = SessionController.class,
    excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
    }
)
class SessionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean FIXSessionRepositoryAdapter sessionRepo;
    @MockBean FIXSessionManager manager;
    @MockBean HotReloadService hotReload;

    private FIXSessionConfig minSession(UUID id) {
        return new FIXSessionConfig(id, "s1", FIXMode.ACCEPTOR, FIXVersion.FIX_44,
            null, "SENDER", "TARGET", "localhost", 9999, 30, true, true);
    }

    private FIXSessionRequest minRequest() {
        return new FIXSessionRequest("s1", "ACCEPTOR", "FIX_44", null,
            "SENDER", "TARGET", "localhost", 9999, 30, true, true);
    }

    @Test
    void createSessionReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        FIXSessionConfig saved = minSession(id);
        when(sessionRepo.save(any())).thenReturn(saved);

        mvc.perform(post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(minRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void putUpdatesSession() throws Exception {
        UUID id = UUID.randomUUID();
        FIXSessionConfig existing = minSession(id);
        when(sessionRepo.findById(id)).thenReturn(Optional.of(existing));
        when(manager.isConnected(id)).thenReturn(false);
        when(sessionRepo.save(any())).thenReturn(existing);

        mvc.perform(put("/api/v1/sessions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(minRequest())))
            .andExpect(status().isOk());
    }

    @Test
    void connectReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(sessionRepo.findById(id)).thenReturn(Optional.of(minSession(id)));
        mvc.perform(put("/api/v1/sessions/" + id + "/connect"))
            .andExpect(status().isOk());
    }

    @Test
    void statusReturnsConnectedFlag() throws Exception {
        UUID id = UUID.randomUUID();
        when(manager.isConnected(id)).thenReturn(true);
        mvc.perform(get("/api/v1/sessions/" + id + "/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connected").value(true));
    }

    @Test
    void putWhileConnectedChangingFixVersionReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        FIXSessionConfig existing = minSession(id); // FIX_44
        when(sessionRepo.findById(id)).thenReturn(Optional.of(existing));
        when(manager.isConnected(id)).thenReturn(true);

        // Different fixVersion -> FIXT_11
        FIXSessionRequest req = new FIXSessionRequest("s1", "ACCEPTOR", "FIXT_11", null,
            "SENDER", "TARGET", "localhost", 9999, 30, true, true);
        mvc.perform(put("/api/v1/sessions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isConflict());
    }
}
