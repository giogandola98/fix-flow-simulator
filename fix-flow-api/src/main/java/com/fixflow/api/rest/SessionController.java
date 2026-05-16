package com.fixflow.api.rest;

import com.fixflow.adapters.persistence.FIXSessionRepositoryAdapter;
import com.fixflow.api.rest.dto.FIXSessionDto;
import com.fixflow.api.rest.dto.FIXSessionRequest;
import com.fixflow.core.domain.session.FIXMode;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.domain.session.FIXVersion;
import com.fixflow.engine.fix.FIXSessionManager;
import com.fixflow.engine.fix.HotReloadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final FIXSessionRepositoryAdapter sessionRepo;
    private final FIXSessionManager manager;
    private final HotReloadService hotReload;

    public SessionController(FIXSessionRepositoryAdapter sessionRepo,
                             FIXSessionManager manager,
                             HotReloadService hotReload) {
        this.sessionRepo = sessionRepo;
        this.manager = manager;
        this.hotReload = hotReload;
    }

    @PostMapping
    public ResponseEntity<FIXSessionDto> create(@RequestBody FIXSessionRequest req) {
        FIXSessionConfig cfg = toConfig(UUID.randomUUID(), req);
        FIXSessionConfig saved = sessionRepo.save(cfg);
        return ResponseEntity.status(201).body(FIXSessionDto.from(saved, false));
    }

    @GetMapping
    public List<FIXSessionDto> list() {
        return sessionRepo.findAll().stream()
            .map(c -> FIXSessionDto.from(c, manager.isConnected(c.id())))
            .toList();
    }

    @GetMapping("/{id}")
    public FIXSessionDto get(@PathVariable UUID id) {
        FIXSessionConfig c = sessionRepo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("session not found: " + id));
        return FIXSessionDto.from(c, manager.isConnected(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody FIXSessionRequest req) {
        FIXSessionConfig existing = sessionRepo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("session not found: " + id));
        boolean connected = manager.isConnected(id);
        FIXVersion newVersion = FIXVersion.valueOf(req.fixVersion());
        if (connected && existing.fixVersion() != newVersion) {
            return ResponseEntity.status(409).body(Map.of(
                "error", "Conflict",
                "message", "Disconnect session before changing FIX version"
            ));
        }
        FIXSessionConfig updated = sessionRepo.save(toConfig(id, req));
        return ResponseEntity.ok(FIXSessionDto.from(updated, connected));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (manager.isConnected(id)) manager.disconnect(id);
        sessionRepo.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/connect")
    public ResponseEntity<Void> connect(@PathVariable UUID id) {
        FIXSessionConfig cfg = sessionRepo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("session not found: " + id));
        manager.connect(cfg);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/disconnect")
    public ResponseEntity<Void> disconnect(@PathVariable UUID id) {
        manager.disconnect(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/status")
    public Map<String, Object> status(@PathVariable UUID id) {
        return Map.of("sessionId", id, "connected", manager.isConnected(id));
    }

    @PostMapping("/{id}/reload")
    public ResponseEntity<Void> reload(@PathVariable UUID id) {
        hotReload.reload(id);
        return ResponseEntity.ok().build();
    }

    private FIXSessionConfig toConfig(UUID id, FIXSessionRequest req) {
        return new FIXSessionConfig(
            id, req.name(), FIXMode.valueOf(req.mode()), FIXVersion.valueOf(req.fixVersion()),
            req.defaultApplVerID(), req.senderCompID(), req.targetCompID(),
            req.host(), req.port(), req.heartbeatInterval(), req.reconnectInterval(),
            req.resetOnLogon(), req.resetOnLogout()
        );
    }
}
