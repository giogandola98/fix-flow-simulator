package com.fixflow.api.rest;

import com.fixflow.adapters.persistence.FIXSessionRepositoryAdapter;
import com.fixflow.api.exception.SessionConflictException;
import com.fixflow.api.rest.dto.FIXSessionDto;
import com.fixflow.api.rest.dto.FIXSessionRequest;
import com.fixflow.core.domain.session.FIXMode;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.domain.session.FIXVersion;
import com.fixflow.engine.fix.FIXSessionManager;
import com.fixflow.engine.fix.HotReloadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

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
        FIXSessionConfig cfg = toConfig(UUID.randomUUID(), req, null);
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
    public FIXSessionDto update(@PathVariable UUID id, @RequestBody FIXSessionRequest req) {
        FIXSessionConfig existing = sessionRepo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("session not found: " + id));
        boolean connected = manager.isConnected(id);
        FIXVersion newVersion = parseEnum(FIXVersion.class, "fixVersion", req.fixVersion());
        if (connected && existing.fixVersion() != newVersion) {
            throw new SessionConflictException("Disconnect session before changing FIX version");
        }
        FIXSessionConfig updated = sessionRepo.save(toConfig(id, req, existing));
        return FIXSessionDto.from(updated, connected);
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

    /**
     * {@code existing} keeps the fields the request does not carry (auto-connect, pack) and the
     * dictionaries when the request leaves them out ({@code null}); an empty string clears one.
     */
    private FIXSessionConfig toConfig(UUID id, FIXSessionRequest req, FIXSessionConfig existing) {
        return new FIXSessionConfig(
            id, req.name(),
            parseEnum(FIXMode.class, "mode", req.mode()),
            parseEnum(FIXVersion.class, "fixVersion", req.fixVersion()),
            req.defaultApplVerID(), req.senderCompID(), req.targetCompID(),
            req.host(), req.port(), req.heartbeatInterval(),
            req.resetOnLogon(), req.resetOnLogout(),
            dictionary(req.appDataDictionary(), existing == null ? null : existing.appDataDictionary()),
            dictionary(req.transportDataDictionary(), existing == null ? null : existing.transportDataDictionary()),
            req.autoConnect() != null ? req.autoConnect() : existing != null && existing.autoConnect(),
            existing == null ? null : existing.packId()
        );
    }

    /**
     * Enum request fields with a clear 400 instead of {@code valueOf}'s "No enum constant …" text,
     * or a 500 from the {@code NullPointerException} a missing field used to raise — see issue #110.
     */
    static <E extends Enum<E>> E parseEnum(Class<E> type, String field, String value) {
        String allowed = Arrays.stream(type.getEnumConstants()).map(Enum::name)
            .collect(Collectors.joining(", "));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field '" + field + "'. Allowed values: " + allowed);
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid " + field + " '" + value + "'. Allowed values: " + allowed, e);
        }
    }

    /** Absent ({@code null}) keeps the stored dictionary, blank clears it, anything else replaces it. */
    private static String dictionary(String requested, String stored) {
        if (requested == null) return stored;
        return requested.isBlank() ? null : requested.trim();
    }
}
