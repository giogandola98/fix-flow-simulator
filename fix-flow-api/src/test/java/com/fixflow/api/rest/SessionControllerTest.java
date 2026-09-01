package com.fixflow.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.adapters.persistence.FIXSessionRepositoryAdapter;
import com.fixflow.api.config.DatabaseAvailability;
import com.fixflow.api.config.GlobalExceptionHandler;
import com.fixflow.api.exception.SessionConflictException;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
// Register the advice explicitly so status mappings (esp. 409) hold regardless of
// test ordering — a prior version was order-dependent and only failed in the full run.
@Import({GlobalExceptionHandler.class, DatabaseAvailability.class})
class SessionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean FIXSessionRepositoryAdapter sessionRepo;
    @MockBean FIXSessionManager manager;
    @MockBean HotReloadService hotReload;

    private FIXSessionConfig session(UUID id, FIXVersion version) {
        return new FIXSessionConfig(id, "s1", FIXMode.ACCEPTOR, version,
            null, "SENDER", "TARGET", "localhost", 9999, 30, true, true);
    }

    private FIXSessionRequest request(String fixVersion) {
        return new FIXSessionRequest("s1", "ACCEPTOR", fixVersion, null,
            "SENDER", "TARGET", "localhost", 9999, 30, true, true);
    }

    @Test
    void createReturns201NotConnected() throws Exception {
        UUID id = UUID.randomUUID();
        when(sessionRepo.save(any())).thenReturn(session(id, FIXVersion.FIX_44));

        mvc.perform(post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request("FIX_44"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.connected").value(false));
    }

    @Test
    void listReturnsSessionsWithConnectedFlag() throws Exception {
        UUID id = UUID.randomUUID();
        when(sessionRepo.findAll()).thenReturn(List.of(session(id, FIXVersion.FIX_44)));
        when(manager.isConnected(id)).thenReturn(true);
        mvc.perform(get("/api/v1/sessions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].connected").value(true));
    }

    @Test
    void getReturnsSession() throws Exception {
        UUID id = UUID.randomUUID();
        when(sessionRepo.findById(id)).thenReturn(Optional.of(session(id, FIXVersion.FIX_44)));
        when(manager.isConnected(id)).thenReturn(false);
        mvc.perform(get("/api/v1/sessions/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.fixVersion").value("FIX_44"));
    }

    @Test
    void getUnknownReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(sessionRepo.findById(id)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/sessions/" + id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateReturnsOkWhenNotConnected() throws Exception {
        UUID id = UUID.randomUUID();
        when(sessionRepo.findById(id)).thenReturn(Optional.of(session(id, FIXVersion.FIX_44)));
        when(manager.isConnected(id)).thenReturn(false);
        when(sessionRepo.save(any())).thenReturn(session(id, FIXVersion.FIXT_11));

        mvc.perform(put("/api/v1/sessions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request("FIXT_11"))))
            .andExpect(status().isOk());
        verify(sessionRepo).save(any());
    }

    @Test
    void updateSameVersionWhileConnectedIsOk() throws Exception {
        UUID id = UUID.randomUUID();
        when(sessionRepo.findById(id)).thenReturn(Optional.of(session(id, FIXVersion.FIX_44)));
        when(manager.isConnected(id)).thenReturn(true);
        when(sessionRepo.save(any())).thenReturn(session(id, FIXVersion.FIX_44));

        mvc.perform(put("/api/v1/sessions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request("FIX_44"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connected").value(true));
    }

    @Test
    void updateVersionChangeWhileConnectedReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(sessionRepo.findById(id)).thenReturn(Optional.of(session(id, FIXVersion.FIX_44)));
        when(manager.isConnected(id)).thenReturn(true);

        mvc.perform(put("/api/v1/sessions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request("FIXT_11"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
        // Must NOT have persisted the conflicting change.
        verify(sessionRepo, never()).save(any());
    }

    @Test
    void updateUnknownReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(sessionRepo.findById(id)).thenReturn(Optional.empty());
        mvc.perform(put("/api/v1/sessions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request("FIX_44"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void connectReturns200Void() throws Exception {
        UUID id = UUID.randomUUID();
        FIXSessionConfig cfg = session(id, FIXVersion.FIX_44);
        when(sessionRepo.findById(id)).thenReturn(Optional.of(cfg));
        mvc.perform(put("/api/v1/sessions/" + id + "/connect"))
            .andExpect(status().isOk());
        verify(manager).connect(cfg);
    }

    @Test
    void connectUnknownReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(sessionRepo.findById(id)).thenReturn(Optional.empty());
        mvc.perform(put("/api/v1/sessions/" + id + "/connect"))
            .andExpect(status().isNotFound());
    }

    @Test
    void disconnectReturns200Void() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(put("/api/v1/sessions/" + id + "/disconnect"))
            .andExpect(status().isOk());
        verify(manager).disconnect(id);
    }

    @Test
    void deleteDisconnectsWhenConnected() throws Exception {
        UUID id = UUID.randomUUID();
        when(manager.isConnected(id)).thenReturn(true);
        mvc.perform(delete("/api/v1/sessions/" + id))
            .andExpect(status().isNoContent());
        verify(manager).disconnect(id);
        verify(sessionRepo).delete(id);
    }

    @Test
    void statusReturnsConnectedFlag() throws Exception {
        UUID id = UUID.randomUUID();
        when(manager.isConnected(id)).thenReturn(true);
        mvc.perform(get("/api/v1/sessions/" + id + "/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connected").value(true))
            .andExpect(jsonPath("$.sessionId").value(id.toString()));
    }

    @Test
    void reloadReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/v1/sessions/" + id + "/reload"))
            .andExpect(status().isOk());
        verify(hotReload).reload(id);
    }

    @Test
    void createWithInvalidEnumReturns400() throws Exception {
        // FIXVersion.valueOf("BOGUS") throws IllegalArgumentException -> 400 via advice.
        mvc.perform(post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request("BOGUS"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }
}
