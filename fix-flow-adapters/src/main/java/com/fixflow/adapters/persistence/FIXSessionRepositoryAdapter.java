package com.fixflow.adapters.persistence;

import com.fixflow.adapters.persistence.entity.FIXSessionEntity;
import com.fixflow.adapters.persistence.jpa.JpaFIXSessionRepository;
import com.fixflow.core.domain.session.FIXSessionConfig;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class FIXSessionRepositoryAdapter {

    private final JpaFIXSessionRepository repo;

    public FIXSessionRepositoryAdapter(JpaFIXSessionRepository repo) { this.repo = repo; }

    @Transactional
    public FIXSessionConfig save(FIXSessionConfig cfg) {
        FIXSessionEntity e = repo.findById(cfg.id()).orElseGet(FIXSessionEntity::new);
        e.setId(cfg.id());
        e.setName(cfg.name());
        e.setMode(cfg.mode());
        e.setFixVersion(cfg.fixVersion());
        e.setDefaultApplVerID(cfg.defaultApplVerID());
        e.setSenderCompID(cfg.senderCompID());
        e.setTargetCompID(cfg.targetCompID());
        e.setHost(cfg.host());
        e.setPort(cfg.port());
        e.setHeartbeatInterval(cfg.heartbeatInterval());
        e.setReconnectInterval(cfg.reconnectInterval());
        e.setResetOnLogon(cfg.resetOnLogon());
        e.setResetOnLogout(cfg.resetOnLogout());
        repo.save(e);
        return cfg;
    }

    @Transactional(readOnly = true)
    public Optional<FIXSessionConfig> findById(UUID id) {
        return repo.findById(id).map(this::toDomain);
    }

    @Transactional(readOnly = true)
    public List<FIXSessionConfig> findAll() {
        return repo.findAll().stream().map(this::toDomain).toList();
    }

    @Transactional
    public void delete(UUID id) { repo.deleteById(id); }

    private FIXSessionConfig toDomain(FIXSessionEntity e) {
        return new FIXSessionConfig(
                e.getId(), e.getName(), e.getMode(), e.getFixVersion(),
                e.getDefaultApplVerID(), e.getSenderCompID(), e.getTargetCompID(),
                e.getHost(), e.getPort(), e.getHeartbeatInterval(),
                e.getReconnectInterval(), e.isResetOnLogon(), e.isResetOnLogout());
    }
}
